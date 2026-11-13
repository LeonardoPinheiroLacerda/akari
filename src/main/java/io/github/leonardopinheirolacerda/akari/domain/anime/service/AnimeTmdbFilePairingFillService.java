package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairingFill;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.UnpairedVideoFile;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Preenchimento automático do pairing TMDB dos arquivos de vídeo da pasta de um anime, pela
 * estratégia do anibridge ({@link EpisodePairingResolutionService}) — usado pelo step
 * {@code TMDB_FILEPAIR} da ingestão.
 *
 * <p>Só grava o que não precisa de decisão: arquivo que já tem pairing gravado é mantido como
 * está, e um episódio resolvido pra mais de um arquivo — ou já pareado com outro — não é
 * gravado em nenhum deles.
 */
@ApplicationScoped
public class AnimeTmdbFilePairingFillService {

    // Motivo do arquivo cujo episódio resolvido também é de outro arquivo da pasta.
    private static final String EPISODE_CONFLICT = "EPISODE_CONFLICT";

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    EpisodePairingResolutionService episodePairingResolutionService;

    @Inject
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    @Inject
    TmdbService tmdbService;

    /**
     * Pareia com episódios do TMDB os arquivos da pasta do anime que ainda não têm pairing.
     *
     * <p>Resolve cada arquivo pelo anibridge e grava o pairing dos resolvidos ({@code PAIRED}),
     * exceto quando o episódio também foi resolvido pra outro arquivo sem pairing ou já está
     * gravado em outro arquivo da pasta — nesses casos nenhum dos arquivos envolvidos é
     * pareado ({@code EPISODE_CONFLICT}). Arquivo com pairing já gravado é mantido como está,
     * mesmo que a resolução aponte outro episódio — só o nome e a sinopse do episódio são
     * regravados com o texto atual do TMDB (mesma cadeia de idiomas do pairing novo, via
     * {@link TmdbService#getSeasonEpisodes}), e só quando mudaram.
     *
     * <p>A resolução, que consulta o anibridge e o TMDB, roda fora de transação; só a gravação
     * roda numa transação curta ({@link #writePairings}).
     *
     * @param anilistId id do anime na AniList
     * @param folderId pasta do anime
     * @param folderName nome da pasta, só pra log
     * @return quantos arquivos foram pareados agora, quantos já tinham pairing, quantos desses
     *         tiveram o texto do episódio atualizado e os que ficaram sem par, com o motivo
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     * @throws BusinessRuleException se o binding TMDB for de filme
     */
    public TmdbFilePairingFill fillPairings(Integer anilistId,
                                            Integer folderId,
                                            String folderName) {

        Log.infof("Preenchendo pairing TMDB da pasta %d (%s)", folderId, folderName);

        final Anime anime = animeTmdbService.findTvBoundAnimeOrThrow(anilistId);

        final List<FilePairingResolution> resolutions = episodePairingResolutionService.resolve(
                anime,
                folderId,
                folderName
        );

        final List<TmdbFilePairing> currentPairings = TmdbFilePairing.findByFolderId(folderId);

        final Set<Integer> pairedFileIds = currentPairings.stream()
                .map(pairing -> pairing.fileId)
                .collect(Collectors.toSet());

        final Set<ResolvedEpisode> pairedEpisodes = currentPairings.stream()
                .filter(pairing -> pairing.seasonNumber != null && pairing.episodeNumber != null)
                .map(pairing -> new ResolvedEpisode(pairing.seasonNumber, pairing.episodeNumber))
                .collect(Collectors.toSet());

        final Map<ResolvedEpisode, Long> proposedEpisodes = resolutions.stream()
                .filter(resolution -> resolution.status() == TmdbFilePairingProposalStatus.PAIRED)
                .filter(resolution -> !pairedFileIds.contains(resolution.videoFile().id))
                .collect(Collectors.groupingBy(
                        FilePairingResolution::resolvedEpisode,
                        Collectors.counting()
                ));

        final Map<Integer, TmdbFilePairingRequest> pairings = new LinkedHashMap<>();
        final List<UnpairedVideoFile> unpaired = new ArrayList<>();

        int kept = 0;

        for (FilePairingResolution resolution : resolutions) {
            if (pairedFileIds.contains(resolution.videoFile().id)) {
                kept++;
                continue;
            }

            if (resolution.status() != TmdbFilePairingProposalStatus.PAIRED) {
                unpaired.add(toUnpaired(resolution, resolution.status().name()));
                continue;
            }

            final ResolvedEpisode episode = resolution.resolvedEpisode();

            if (proposedEpisodes.get(episode) > 1 || pairedEpisodes.contains(episode)) {
                unpaired.add(toUnpaired(resolution, EPISODE_CONFLICT));
                continue;
            }

            pairings.put(resolution.videoFile().id, resolution.pairing());
        }

        final Map<Integer, TmdbEpisodeResponse> textUpdates = findTextUpdates(
                anime.tmdbId,
                currentPairings
        );

        writePairings(pairings, textUpdates);

        Log.infof(
                "Pairing TMDB da pasta %d preenchido: %d pareados, %d mantidos "
                        + "(%d com texto atualizado), %d sem par",
                folderId,
                pairings.size(),
                kept,
                textUpdates.size(),
                unpaired.size()
        );

        return new TmdbFilePairingFill(pairings.size(), kept, textUpdates.size(), unpaired);
    }

    /**
     * Grava os pairings novos e regrava o nome/sinopse dos já existentes, numa transação curta.
     * Package-private (não {@code private}) pra que o {@code @Transactional} seja interceptado
     * quando chamado por {@link #fillPairings}.
     *
     * @param pairings pairing novo de cada arquivo, pelo id do arquivo
     * @param textUpdates episódio com o texto atual, pelo id do arquivo já pareado cujo texto
     *                    mudou
     */
    @Transactional
    void writePairings(Map<Integer, TmdbFilePairingRequest> pairings,
                       Map<Integer, TmdbEpisodeResponse> textUpdates) {

        pairings.forEach((fileId, request) -> {
            final TmdbFilePairing pairing = new TmdbFilePairing();
            pairing.fileId = fileId;

            animeTmdbFilePairingMapper.applyRequest(request, pairing);

            pairing.persist();
        });

        textUpdates.forEach((fileId, episode) -> TmdbFilePairing
                .find(fileId)
                .ifPresent(pairing -> {
                    pairing.episodeName = episode.name();
                    pairing.episodeOverview = episode.overview();
                }));
    }

    /**
     * Episódio com o texto atual de cada pairing já gravado cujo nome ou sinopse mudou, sem
     * mexer na temporada/episódio gravados. Cada temporada é buscada uma vez.
     *
     * @param tmdbId série do TMDB vinculada ao anime
     * @param currentPairings pairings já gravados na pasta
     * @return o episódio pelo id do arquivo, só dos que precisam ser regravados
     */
    private Map<Integer, TmdbEpisodeResponse> findTextUpdates(Integer tmdbId,
                                                             List<TmdbFilePairing> currentPairings) {

        final Map<Integer, Map<Integer, TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();
        final Map<Integer, TmdbEpisodeResponse> textUpdates = new LinkedHashMap<>();

        for (TmdbFilePairing pairing : currentPairings) {
            if (pairing.seasonNumber == null) {
                continue;
            }

            if (pairing.episodeNumber == null) {
                continue;
            }

            final TmdbEpisodeResponse episode = episodesBySeason
                    .computeIfAbsent(
                            pairing.seasonNumber,
                            seasonNumber -> episodesByNumber(tmdbId, seasonNumber)
                    )
                    .get(pairing.episodeNumber);

            if (episode == null) {
                continue;
            }

            if (Objects.equals(episode.name(), pairing.episodeName)
                    && Objects.equals(episode.overview(), pairing.episodeOverview)) {
                continue;
            }

            textUpdates.put(pairing.fileId, episode);
        }

        return textUpdates;
    }

    private Map<Integer, TmdbEpisodeResponse> episodesByNumber(Integer tmdbId, Integer seasonNumber) {
        return RetryUtils
                .withRateLimitRetry(() -> tmdbService.getSeasonEpisodes(tmdbId, seasonNumber))
                .stream()
                .filter(episode -> episode.episodeNumber() != null)
                .collect(Collectors.toMap(
                        TmdbEpisodeResponse::episodeNumber,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    private static UnpairedVideoFile toUnpaired(FilePairingResolution resolution, String reason) {
        final ResolvedEpisode episode = resolution.resolvedEpisode();

        return new UnpairedVideoFile(
                resolution.videoFile().id,
                resolution.videoFile().name,
                reason,
                episode == null ? null : episode.seasonNumber(),
                episode == null ? null : episode.tmdbEpisode()
        );
    }
}
