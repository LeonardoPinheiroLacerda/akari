package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AbsoluteEpisodeOffsetService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Segunda etapa da resolução do pairing: prepara, por pasta, a conversão pelo anibridge do
 * número de episódio de cada arquivo pra temporada/episódio do TMDB
 * ({@link AnibridgeFolderMapping}) — só pelos segments que apontam pra série vinculada, pra nunca
 * aplicar a numeração de um título sobre outro.
 *
 * <p>Decide aqui, olhando a pasta inteira, se ela usa numeração absoluta, porque o offset só vale
 * nos segments que cobrem a temporada isolada com segurança — a numeração absoluta depende do
 * mapeamento do anibridge e não é uma etapa à parte.
 */
@ApplicationScoped
public class AnibridgeEpisodeMapper {

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    AbsoluteEpisodeOffsetService absoluteEpisodeOffsetService;

    /**
     * Prepara a conversão dos arquivos de uma pasta.
     *
     * <p>Sem mapeamento do anibridge pra série vinculada, ou com mapeamento conflitante, a
     * conversão sai com {@code NO_MAPPING} ou {@code CONFLICTING_MAPPING} pra todos os arquivos.
     * Quando a pasta inteira usa numeração absoluta (ver {@link #findFolderOffset}), o offset vale
     * pra todos os arquivos, inclusive os cujo número também caberia no intervalo do anime.
     *
     * @param anime anime com binding TMDB de série
     * @param episodeNumbers número de episódio de cada arquivo da pasta
     * @param fileCount quantidade de arquivos da pasta
     * @return a conversão da pasta
     */
    public AnibridgeFolderMapping prepare(Anime anime,
                                          List<OptionalInt> episodeNumbers,
                                          Integer fileCount) {

        final Optional<TmdbMappingCandidate> candidate = anibridgeResolutionService
                .resolveCandidateFor(
                        anime.anilistId,
                        anime.malId,
                        anime.tmdbId,
                        anime.tmdbMediaType
                );

        if (candidate.isEmpty()) {
            return AnibridgeFolderMapping.unusable(TmdbFilePairingProposalStatus.NO_MAPPING);
        }

        final Optional<List<MappingSegment>> safeSegments = anibridgeResolutionService
                .resolveSafeSegments(candidate.get());

        if (safeSegments.isEmpty()) {
            return AnibridgeFolderMapping.unusable(
                    TmdbFilePairingProposalStatus.CONFLICTING_MAPPING
            );
        }

        // Pasta de 1 arquivo + anime de 1 episódio: aquele arquivo só pode ser o episódio 1 —
        // usado como último recurso, depois de qualquer outra tentativa falhar. Cobre tanto o
        // arquivo sem número reconhecível quanto o caso comum de OVA/special numerado pela
        // contagem absoluta da série principal (ex.: "Clannad After Story - 25 OVA"), que não
        // bate com o range local (1-1) do segment do anibridge pra esse special.
        final boolean singleEpisodeFolder = fileCount == 1
                && anime.episodes != null
                && anime.episodes == 1;

        final List<Integer> offsetCandidates = absoluteEpisodeOffsetService.computeOffsetCandidates(
                anime.anilistId
        );

        final Optional<List<MappingSegment>> offsetSegments = offsetCandidates.isEmpty()
                ? Optional.empty()
                : anibridgeResolutionService.restrictToFullSeasonCoverage(
                        safeSegments.get(),
                        anime.episodes
                );

        final OptionalInt folderOffset = offsetSegments.isPresent()
                ? findFolderOffset(
                        episodeNumbers,
                        safeSegments.get(),
                        offsetSegments.get(),
                        offsetCandidates
                )
                : OptionalInt.empty();

        return AnibridgeFolderMapping.usable(
                anibridgeResolutionService,
                safeSegments.get(),
                offsetSegments.orElse(null),
                offsetCandidates,
                folderOffset,
                singleEpisodeFolder
        );
    }

    /**
     * Offset de numeração absoluta da pasta inteira, quando ela usa essa numeração: algum
     * arquivo não resolve direto, e entre os offsets da cadeia de prequels fica o que resolve
     * mais arquivos da pasta — desempate pelo mais próximo —, considerando só os offsets menores
     * que todos os números da pasta. Aplicado a todos os arquivos, corrige o arquivo cujo número
     * absoluto também cabe no intervalo do anime e seria resolvido direto, no episódio errado
     * (ex.: {@code S01E12} no {@code 86 Part 2}, offset 11, viraria o episódio 12 em vez do 1),
     * e o arquivo que um offset mais próximo resolveria errado (ex.: no {@code Boku no Hero
     * Academia 7}, arquivos 139–159, o offset 125 resolve só 139–146, e o 138 resolve todos). A
     * exigência de números maiores que o offset protege a pasta que mistura numeração local e
     * absoluta, onde o offset deslocaria os arquivos de numeração local.
     *
     * @return o offset da pasta, ou vazio se nenhum arquivo precisa de offset ou nenhum offset
     *         resolve arquivo da pasta
     */
    private OptionalInt findFolderOffset(List<OptionalInt> folderEpisodeNumbers,
                                         List<MappingSegment> safeSegments,
                                         List<MappingSegment> offsetSegments,
                                         List<Integer> offsetCandidates) {

        final List<Integer> episodeNumbers = folderEpisodeNumbers.stream()
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .toList();

        final boolean needsOffset = episodeNumbers.stream()
                .anyMatch(episodeNumber -> anibridgeResolutionService.resolveEpisode(
                        safeSegments,
                        episodeNumber
                ).isEmpty());

        if (!needsOffset) {
            return OptionalInt.empty();
        }

        OptionalInt folderOffset = OptionalInt.empty();
        long folderOffsetCoverage = 0;

        for (Integer offset : offsetCandidates) {
            if (episodeNumbers.stream().anyMatch(episodeNumber -> episodeNumber <= offset)) {
                continue;
            }

            final long coverage = episodeNumbers.stream()
                    .filter(episodeNumber -> anibridgeResolutionService.resolveEpisode(
                            offsetSegments,
                            episodeNumber - offset
                    ).isPresent())
                    .count();

            if (coverage > folderOffsetCoverage) {
                folderOffset = OptionalInt.of(offset);
                folderOffsetCoverage = coverage;
            }
        }

        return folderOffset;
    }

}
