package io.github.leonardopinheirolacerda.akari.domain.anibridge.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.CandidateResolution;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.CoverageResult;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonSummaryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve ambiguidade e colisão nos segments crus do anibridge — escolhe o candidato TMDB
 * confiável quando um anime bate em mais de um, converte episódio do anime em episódio do TMDB,
 * e confirma no TMDB a validade de segments conflitantes. Fica à parte de
 * {@link AnibridgeMappingService} só pra manter a separação "busca crua" (lá) vs. "julgamento
 * sobre a qualidade do dado" (aqui) — quem consome recebe o resultado já resolvido, nunca a
 * lista crua de segments conflitantes.
 */
@ApplicationScoped
public class AnibridgeResolutionService {

    @Inject
    AnibridgeMappingService anibridgeMappingService;

    @Inject
    TmdbService tmdbService;

    /**
     * Escolhe o candidato TMDB pro anime — primeiro tenta pelo {@code anilistId}, cai pro
     * {@code malId} se não achar nada. Entre os candidatos de um mesmo anime ({@code tmdbId}s
     * diferentes), nesta ordem: candidato não suspeito ganha de suspeito (ver {@link #isSuspicious})
     * — sobreposição interna, ou cobertura além do que o anime tem de verdade —; depois a maior
     * cobertura (já descontada a sobreposição, ver {@link #analyzeCoverage}); por fim o tipo que
     * combina com o formato do anime na AniList ({@code MOVIE} → filme; {@code TV},
     * {@code TV_SHORT} e {@code ONA} → série). Sem isso, um segment com número inflado podia
     * ganhar de outro candidato correto só por ter um total maior, e um filme que também aparece
     * como especial da série empatava com ela. Empate que sobra fica com o primeiro candidato, sem
     * ordem definida — ver {@link #resolveCandidates}.
     *
     * @param anilistId id do anime na AniList
     * @param malId id do anime no MyAnimeList, ou {@code null} se não houver
     * @param totalEpisodes total de episódios do anime segundo a AniList ({@code anime.episodes}),
     *                       ou {@code null} se desconhecido — sem ele, só a sobreposição é
     *                       considerada suspeita
     * @param format formato do anime na AniList (ex.: {@code TV}, {@code MOVIE}), ou {@code null}
     *               se desconhecido — sem ele, o tipo não desempata
     * @return o melhor candidato, ou vazio se o anibridge não tem nada pra esse anime
     */
    public Optional<TmdbMappingCandidate> resolveBestCandidate(Integer anilistId,
                                                               Integer malId,
                                                               Integer totalEpisodes,
                                                               String format) {
        return Optional.ofNullable(resolveCandidates(anilistId, malId, totalEpisodes, format).best());
    }

    /**
     * Escolhe o melhor candidato pelos mesmos critérios do {@link #resolveBestCandidate} e diz,
     * na mesma busca, quantos candidatos existem, se o melhor é suspeito e se ele empata com o
     * segundo em todos os critérios — o que quem decide automaticamente precisa pra não confiar
     * numa escolha sem critério. A cobertura de cada candidato é calculada uma vez só.
     *
     * @param anilistId id do anime na AniList
     * @param malId id do anime no MyAnimeList, ou {@code null} se não houver
     * @param totalEpisodes total de episódios do anime segundo a AniList, ou {@code null}
     * @param format formato do anime na AniList, ou {@code null}
     * @return a escolha; {@code best} nulo e contagem zero se o anibridge não tem nada pra esse
     *         anime
     */
    public CandidateResolution resolveCandidates(Integer anilistId,
                                                 Integer malId,
                                                 Integer totalEpisodes,
                                                 String format) {
        final List<TmdbMappingCandidate> candidates = findSegments(anilistId, malId)
                .stream()
                .collect(Collectors.groupingBy(segment -> segment.tmdbId + ":" + segment.tmdbMediaType))
                .values()
                .stream()
                .map(segments -> new TmdbMappingCandidate(
                        segments.get(0).tmdbId,
                        segments.get(0).tmdbMediaType,
                        segments
                ))
                .toList();

        if (candidates.isEmpty()) {
            return new CandidateResolution(null, 0, false, false);
        }

        final Map<TmdbMappingCandidate, CoverageResult> coverages = candidates.stream()
                .collect(Collectors.toMap(Function.identity(), candidate -> analyzeCoverage(candidate.segments())));

        final Comparator<TmdbMappingCandidate> order = candidateOrder(coverages, totalEpisodes, format);

        final List<TmdbMappingCandidate> ranked = candidates.stream()
                .sorted(order)
                .toList();

        final TmdbMappingCandidate best = ranked.get(0);

        return new CandidateResolution(
                best,
                ranked.size(),
                isSuspicious(coverages.get(best), totalEpisodes),
                ranked.size() > 1 && order.compare(best, ranked.get(1)) == 0
        );
    }

    /**
     * Monta o candidato do anibridge pra uma série/filme do TMDB já escolhido — só os segments do
     * anime que apontam pra esse {@code tmdbId}/{@code mediaType}. Primeiro pelo
     * {@code anilistId}, cai pro {@code malId} se não achar nada. Diferente de
     * {@link #resolveBestCandidate}, não escolhe entre candidatos: serve pra quem já tem o
     * binding (ex.: pairing de episódios), pra nunca aplicar a numeração de um título sobre outro.
     *
     * @param anilistId id do anime na AniList
     * @param malId id do anime no MyAnimeList, ou {@code null} se não houver
     * @param tmdbId id da série/filme vinculado no TMDB
     * @param mediaType tipo do binding
     * @return o candidato com os segments desse título, ou vazio se o anibridge não mapeia o
     *         anime pra ele
     */
    public Optional<TmdbMappingCandidate> resolveCandidateFor(Integer anilistId,
                                                              Integer malId,
                                                              Integer tmdbId,
                                                              TmdbMediaType mediaType) {

        List<MappingSegment> segments = segmentsPointingTo(
                anibridgeMappingService.findByAnilistId(anilistId),
                tmdbId,
                mediaType
        );

        if (segments.isEmpty() && malId != null) {
            segments = segmentsPointingTo(
                    anibridgeMappingService.findByMalId(malId),
                    tmdbId,
                    mediaType
            );
        }

        if (segments.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(new TmdbMappingCandidate(tmdbId, mediaType, segments));
    }

    /**
     * Converte um número de episódio do anime pra temporada/episódio correspondente no TMDB,
     * usando o segment cujo range contém esse episódio.
     *
     * @param segments segments do candidato escolhido (ver {@link #resolveBestCandidate})
     * @param episodeNumber episódio do anime
     * @return a temporada/episódio no TMDB, ou vazio se nenhum segment cobre esse número
     */
    public Optional<ResolvedEpisode> resolveEpisode(List<MappingSegment> segments, Integer episodeNumber) {
        for (MappingSegment segment : segments) {
            if (!covers(segment, episodeNumber)) {
                continue;
            }

            final int ratio = segment.ratio == null
                    ? 1
                    : segment.ratio;

            if (ratio <= 0) {
                continue;
            }

            final int offset = (episodeNumber - segment.animeEpisodeStart) / ratio;
            final int tmdbEpisode = segment.tmdbEpisodeStart + offset;

            final ResolvedEpisode resolved = new ResolvedEpisode(segment.seasonNumber, tmdbEpisode);

            return Optional.of(resolved);
        }

        return Optional.empty();
    }

    /**
     * Confirma se o candidato tem segments com range de episódio sobreposto entre si — sinal de
     * duas interpretações conflitantes pro mesmo trecho (caso real encontrado no dataset: um
     * show que o TMDB reestruturou pra uma única temporada tinha, além do segment certo, um
     * segment residual de uma season antiga cobrindo o mesmo range). Não compara temporada —
     * dois segments de temporadas diferentes mas sem sobreposição de episódio (uma franquia cujo
     * mapeamento emenda season 1 e season 2, por exemplo) não conta como conflito.
     *
     * <p>Mesma detecção usada por {@link #resolveBestCandidate} (ver {@link #analyzeCoverage}) —
     * uma fonte só pra "isso tem sobreposição", nunca duas lógicas calculando a mesma coisa de
     * jeitos diferentes.
     *
     * @param segments segments do candidato escolhido (ver {@link #resolveBestCandidate})
     * @return {@code true} se existir ao menos um par de segments com range sobreposto
     */
    public boolean hasOverlap(List<MappingSegment> segments) {
        return analyzeCoverage(segments).hasOverlap();
    }

    /**
     * Restringe os segments (já livres de sobreposição — ver {@link #hasOverlap}) a uma única
     * temporada cobrindo do episódio 1 até {@code totalEpisodes} — confirma que o candidato
     * representa uma temporada isolada e completa da franquia, sem emendar mais de uma
     * temporada (ex.: um anime cujo mapeamento do anibridge cobre season 1 e season 2 em
     * sequência, sem repetir episódio, não passa essa validação).
     *
     * @param segments segments já livres de sobreposição (ver {@link #hasOverlap})
     * @param totalEpisodes total de episódios da temporada segundo a AniList ({@code anime.episodes})
     * @return os segments, se forem de uma única temporada cobrindo 1..{@code totalEpisodes} (ou
     *         até um segment aberto); vazio caso contrário
     */
    public Optional<List<MappingSegment>> restrictToFullSeasonCoverage(List<MappingSegment> segments,
                                                                       Integer totalEpisodes) {

        if (totalEpisodes == null) {
            return Optional.empty();
        }

        final Set<Integer> seasonNumbers = segments.stream()
                .map(segment -> segment.seasonNumber)
                .collect(Collectors.toSet());

        if (seasonNumbers.size() > 1) {
            return Optional.empty();
        }

        final boolean coversStart = segments.stream()
                .anyMatch(segment -> covers(segment, 1));

        final boolean coversEnd = segments.stream()
                .anyMatch(segment -> segment.animeEpisodeEnd == null || covers(segment, totalEpisodes));

        if (!coversStart || !coversEnd) {
            return Optional.empty();
        }

        return Optional.of(segments);
    }

    /**
     * Resolve os segments seguros pra usar no pairing. Sem sobreposição de range entre eles
     * (ver {@link #hasOverlap}), usa a lista crua direto — caso comum, sem ambiguidade, sem
     * precisar confirmar temporada real no TMDB. Só paga essa chamada extra quando existe
     * conflito de fato pra resolver, tentando descartar o(s) segment(s) residual(is)/errado(s)
     * do anibridge que apontam pra uma temporada que o show nem tem mais. Só pra {@code TV};
     * filme não tem a noção de "temporada" que a validação resolve.
     *
     * @param candidate candidato escolhido (ver {@link #resolveBestCandidate})
     * @return os segments seguros pra usar, ou vazio se o conflito não foi resolvido (continua
     *         ambíguo mesmo depois de descartar temporadas inexistentes no TMDB)
     */
    public Optional<List<MappingSegment>> resolveSafeSegments(TmdbMappingCandidate candidate) {
        final List<MappingSegment> rawSegments = candidate.segments();

        if (!hasOverlap(rawSegments)) {
            return Optional.of(rawSegments);
        }

        if (candidate.mediaType() != TmdbMediaType.TV) {
            return Optional.empty();
        }

        final Set<Integer> existingSeasonNumbers;

        try {
            existingSeasonNumbers = RetryUtils.withRateLimitRetry(() -> tmdbService
                    .getTvDetails(candidate.tmdbId(), false)
                    .seasons()
                    .stream()
                    .map(TmdbSeasonSummaryResponse::seasonNumber)
                    .collect(Collectors.toSet()));

        } catch (IntegrationException e) {
            Log.warnf(e, "Busca das temporadas do TMDB %d falhou", candidate.tmdbId());
            return Optional.empty();
        }

        final List<MappingSegment> validSegments = rawSegments.stream()
                .filter(segment -> existingSeasonNumbers.contains(segment.seasonNumber))
                .toList();

        if (validSegments.isEmpty() || hasOverlap(validSegments)) {
            return Optional.empty();
        }

        return Optional.of(validSegments);
    }

    private static List<MappingSegment> segmentsPointingTo(List<MappingSegment> segments,
                                                           Integer tmdbId,
                                                           TmdbMediaType mediaType) {

        return segments.stream()
                .filter(segment -> segment.tmdbId.equals(tmdbId))
                .filter(segment -> segment.tmdbMediaType == mediaType)
                .toList();
    }

    /**
     * Segments do anime pelo {@code anilistId}; se não houver nenhum, pelo {@code malId}.
     */
    private List<MappingSegment> findSegments(Integer anilistId, Integer malId) {
        final List<MappingSegment> segments = anibridgeMappingService.findByAnilistId(anilistId);

        if (segments.isEmpty() && malId != null) {
            return anibridgeMappingService.findByMalId(malId);
        }

        return segments;
    }

    /**
     * Ordem dos candidatos, do melhor pro pior: não suspeito antes de suspeito, maior cobertura
     * antes, e tipo que combina com o formato do anime antes.
     */
    private Comparator<TmdbMappingCandidate> candidateOrder(Map<TmdbMappingCandidate, CoverageResult> coverages,
                                                            Integer totalEpisodes,
                                                            String format) {
        final TmdbMediaType preferredMediaType = preferredMediaTypeOf(format);

        return Comparator
                .comparing((TmdbMappingCandidate candidate) -> isSuspicious(coverages.get(candidate), totalEpisodes))
                .thenComparing(
                        candidate -> coverages.get(candidate).episodeCount(),
                        Comparator.reverseOrder()
                )
                .thenComparing(candidate -> candidate.mediaType() != preferredMediaType);
    }

    /**
     * Tipo do TMDB que combina com o formato da AniList: {@code MOVIE} → filme; {@code TV},
     * {@code TV_SHORT} e {@code ONA} → série; os demais ({@code OVA}, {@code SPECIAL}...) e o
     * formato desconhecido não têm preferência.
     */
    private static TmdbMediaType preferredMediaTypeOf(String format) {
        if (format == null) {
            return null;
        }

        return switch (format) {
            case "MOVIE" -> TmdbMediaType.MOVIE;
            case "TV", "TV_SHORT", "ONA" -> TmdbMediaType.TV;
            default -> null;
        };
    }

    /**
     * Um candidato é suspeito quando tem sobreposição interna (ver {@link #hasOverlap}), ou
     * quando cobre mais episódios do que o anime tem de verdade — de nada adianta um segment
     * dizendo cobrir 300 episódios numa série de 12, isso só significa dado errado, não
     * cobertura melhor. Segment aberto ({@code episodeCount == Integer.MAX_VALUE}) nunca é
     * suspeito por esse motivo — cobertura indefinida não tem "demais" pra comparar.
     */
    private static boolean isSuspicious(CoverageResult coverage, Integer totalEpisodes) {
        if (coverage.hasOverlap()) {
            return true;
        }

        if (coverage.episodeCount() == Integer.MAX_VALUE) {
            return false;
        }

        return totalEpisodes != null && coverage.episodeCount() > totalEpisodes;
    }

    private static boolean covers(MappingSegment segment, Integer episodeNumber) {
        if (episodeNumber < segment.animeEpisodeStart) {
            return false;
        }

        return segment.animeEpisodeEnd == null || episodeNumber <= segment.animeEpisodeEnd;
    }

    /**
     * Mescla range sobreposto entre os segments e soma a cobertura de episódios distintos
     * resultante, sinalizando se alguma sobreposição foi encontrada no processo — única
     * implementação da detecção de sobreposição do serviço (ver {@link #hasOverlap}), pra nunca
     * ter duas lógicas calculando a mesma coisa de jeitos diferentes.
     */
    private static CoverageResult analyzeCoverage(List<MappingSegment> segments) {
        for (MappingSegment segment : segments) {
            if (segment.animeEpisodeEnd == null) {
                return new CoverageResult(Integer.MAX_VALUE, false);
            }
        }

        final List<MappingSegment> sorted = segments.stream()
                .sorted(Comparator.comparingInt(segment -> segment.animeEpisodeStart))
                .toList();

        int total = 0;
        boolean overlapFound = false;
        int mergedStart = sorted.get(0).animeEpisodeStart;
        int mergedEnd = sorted.get(0).animeEpisodeEnd;

        for (MappingSegment segment : sorted.subList(1, sorted.size())) {
            if (segment.animeEpisodeStart <= mergedEnd) {
                overlapFound = true;
                mergedEnd = Math.max(mergedEnd, segment.animeEpisodeEnd);
                continue;
            }

            total += mergedEnd - mergedStart + 1;
            mergedStart = segment.animeEpisodeStart;
            mergedEnd = segment.animeEpisodeEnd;
        }

        total += mergedEnd - mergedStart + 1;

        return new CoverageResult(total, overlapFound);
    }

}
