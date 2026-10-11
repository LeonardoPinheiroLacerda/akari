package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeReviewStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbSearchService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Liga uma pasta automaticamente: busca/vincula AniList, resolve/vincula TMDB via anibridge, e
 * pareia os episódios se o TMDB mapeado for série. Chamado pela ingestão uma pasta por vez,
 * cada pasta inteiramente processada antes da próxima.
 *
 * <p>Idempotente em cada estágio — pode ser chamado de novo numa pasta já (parcialmente) ligada
 * sem duplicar nada nem travar, já que a reconciliação roda de novo a cada disparo. Nunca lança
 * — qualquer falha isolada na pasta vira notificação {@code WARNING}, nunca aborta as outras.
 *
 * <p>Também atualiza {@code anime.reviewStatus} ao final de cada tentativa (via
 * {@link AnimeService#updateReviewStatusIfNotReviewed}) — {@code PENDING_REVIEW} quando o
 * binding/pareamento termina completo, {@code PARTIAL} quando fica incompleto por qualquer
 * motivo. Nunca sobrescreve um anime já marcado {@code REVIEWED} manualmente.
 *
 * <p>Não tem {@code @Transactional} próprio — só orquestra chamadas a outros services, cada um
 * já transacional na sua própria operação. Isso evita segurar uma transação aberta durante as
 * chamadas externas (AniList/TMDB), que podem demorar bastante em caso de rate limit (ver
 * {@link RetryUtils}).
 */
@ApplicationScoped
public class FolderAutoBindService {

    @Inject
    AnimeService animeService;

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    AnimeTmdbFilePairingService animeTmdbFilePairingService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    VideoFileService videoFileService;

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    AbsoluteEpisodeOffsetService absoluteEpisodeOffsetService;

    @Inject
    TmdbSearchService tmdbSearchService;

    @Inject
    NotificationService notificationService;

    /**
     * @param folderId id da pasta no catalog
     * @param folderName nome da pasta — usado como query de busca na AniList quando a pasta
     *                    ainda não tem anime vinculado
     */
    public void autoBindFolder(Integer folderId, String folderName) {
        try {
            doAutoBindFolder(folderId, folderName);
        } catch (Exception e) {
            Log.errorf(e, "Auto-bind da pasta %d falhou de forma inesperada", folderId);
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Falha inesperada ao vincular automaticamente",
                    folderId,
                    null);
        }
    }

    private void doAutoBindFolder(Integer folderId, String folderName) {
        final Anime anime = bindAnilist(folderId, folderName);

        if (anime == null) {
            return;
        }

        final Anime boundAnime = bindTmdb(folderId, folderName, anime);

        if (boundAnime.tmdbId == null) {
            animeService.updateReviewStatusIfNotReviewed(boundAnime.anilistId, AnimeReviewStatus.PARTIAL);
            return;
        }

        if (boundAnime.tmdbMediaType != TmdbMediaType.TV) {
            // Filme não tem pairing de episódio — o bind TMDB sozinho já é a ingestão completa.
            animeService.updateReviewStatusIfNotReviewed(boundAnime.anilistId, AnimeReviewStatus.PENDING_REVIEW);
            return;
        }

        pairEpisodes(folderId, folderName, boundAnime);
    }

    private Anime bindAnilist(Integer folderId, String folderName) {
        final Optional<Anime> existing = animeService.findByFolder(folderId);

        if (existing.isPresent()) {
            return existing.get();
        }

        final Integer anilistId;

        try {
            anilistId = RetryUtils.withRateLimitRetry(() -> findUnboundAnilistCandidate(folderName));
        } catch (IntegrationException e) {
            Log.warnf(e, "Busca na AniList falhou pra pasta %d (%s)", folderId, folderName);
            notificationService.notify(
                    NotificationLevel.WARNING, folderName, "Busca na AniList falhou", folderId, null);
            return null;
        }

        if (anilistId == null) {
            Log.warnf("Nenhum resultado utilizável na AniList pra pasta %d (%s)", folderId, folderName);
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Não foi encontrado um título correspondente na AniList",
                    folderId,
                    null);
            return null;
        }

        try {
            RetryUtils.withRateLimitRetry(
                    () -> {
                        final AnimeBindingRequest request = new AnimeBindingRequest()
                                .folderId(folderId);
                        return animeService.bindAnimeToFolder(anilistId, request);
                    });
        } catch (IntegrationException e) {
            Log.warnf(e, "Bind AniList %d falhou pra pasta %d (%s)", anilistId, folderId, folderName);
            notificationService.notify(
                    NotificationLevel.WARNING, folderName, "Bind na AniList falhou", folderId, null);
            return null;
        }

        return animeService.findByFolder(folderId)
                .orElse(null);
    }

    /**
     * Busca na AniList e devolve o primeiro resultado cujo {@code anilistId} ainda não tem
     * metadata salva — evita colidir quando duas pastas diferentes baterem no mesmo título.
     */
    private Integer findUnboundAnilistCandidate(String folderName) {
        final AnilistMediaSummaryPage results = anilistSearchService.search(
                folderName,
                0,
                5,
                false
        );

        for (AnilistMediaSummary summary : results.getData()) {
            if (!animeService.exists(summary.getAnilistId())) {
                return summary.getAnilistId();
            }
        }

        return null;
    }

    private Anime bindTmdb(Integer folderId, String folderName, Anime anime) {
        if (anime.tmdbId != null) {
            return anime;
        }

        final Optional<TmdbMappingCandidate> candidate = anibridgeResolutionService.resolveBestCandidate(
                anime.anilistId,
                anime.malId,
                anime.episodes
        );

        if (candidate.isEmpty()) {
            Log.warnf("Nenhum mapeamento TMDB encontrado pro anime %d (pasta %d)", anime.anilistId, folderId);
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Não foi encontrado um mapeamento TMDB pra esse anime",
                    folderId,
                    null);
            return anime;
        }

        final TmdbMappingCandidate best = candidate.get();

        try {
            final TmdbBindingRequest request = new TmdbBindingRequest()
                    .tmdbId(best.tmdbId())
                    .mediaType(best.mediaType());

            RetryUtils.withRateLimitRetry(() -> animeTmdbService.bindTmdb(
                    anime.anilistId,
                    request));

        } catch (IntegrationException | ResourceNotFoundException e) {
            Log.warnf(e, "Bind TMDB %d falhou pro anime %d (pasta %d)", best.tmdbId(), anime.anilistId, folderId);
            notificationService.notify(
                    NotificationLevel.WARNING, folderName, "Bind no TMDB falhou", folderId, null);
            return anime;
        }

        return animeService.findByFolder(folderId)
                .orElse(anime);
    }

    private void pairEpisodes(Integer folderId, String folderName, Anime anime) {

        final Optional<TmdbMappingCandidate> candidate = anibridgeResolutionService.resolveBestCandidate(
                anime.anilistId,
                anime.malId,
                anime.episodes
        );

        if (candidate.isEmpty()) {
            animeService.updateReviewStatusIfNotReviewed(anime.anilistId, AnimeReviewStatus.PARTIAL);
            return;
        }

        final Optional<List<MappingSegment>> safeSegments = anibridgeResolutionService.resolveSafeSegments(candidate.get());

        if (safeSegments.isEmpty()) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Mapeamento do anibridge tem temporadas conflitantes pra esse anime — "
                            + "pareamento automático não tentado",
                    folderId,
                    null);
            animeService.updateReviewStatusIfNotReviewed(anime.anilistId, AnimeReviewStatus.PARTIAL);
            return;
        }

        final List<VideoFile> videoFiles = videoFileService.listByFolder(folderId);
        final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();

        // Pasta de 1 arquivo + anime de 1 episódio: aquele arquivo só pode ser o episódio 1 —
        // usado como último recurso, depois de qualquer outra tentativa falhar. Cobre tanto o
        // arquivo sem número reconhecível quanto o caso comum de OVA/special numerado pela
        // contagem absoluta da série principal (ex.: "Clannad After Story - 25 OVA"), que não
        // bate com o range local (1-1) do segment do anibridge pra esse special.
        final boolean singleEpisodeFolder = videoFiles.size() == 1
                && anime.episodes != null
                && anime.episodes == 1;

        final List<Integer> absoluteOffsetCandidates = absoluteEpisodeOffsetService.computeOffsetCandidates(anime.anilistId);

        final Optional<List<MappingSegment>> offsetSegments = absoluteOffsetCandidates.isEmpty()
                ? Optional.empty()
                : anibridgeResolutionService.restrictToFullSeasonCoverage(safeSegments.get(), anime.episodes);

        int paired = 0;
        boolean ambiguousAbsoluteNumbering = false;

        for (VideoFile videoFile : videoFiles) {
            final OptionalInt episodeNumber = EpisodeNumberExtractor.extract(videoFile.name);

            Optional<ResolvedEpisode> resolved = Optional.empty();
            boolean offsetNeededButIneligible = false;

            if (episodeNumber.isPresent()) {
                resolved = anibridgeResolutionService.resolveEpisode(
                        safeSegments.get(),
                        episodeNumber.getAsInt()
                );

                if (resolved.isEmpty() && !absoluteOffsetCandidates.isEmpty()) {
                    if (offsetSegments.isPresent()) {
                        for (Integer offsetCandidate : absoluteOffsetCandidates) {
                            resolved = anibridgeResolutionService.resolveEpisode(
                                    offsetSegments.get(),
                                    episodeNumber.getAsInt() - offsetCandidate
                            );

                            if (resolved.isPresent()) {
                                break;
                            }
                        }
                    } else {
                        offsetNeededButIneligible = true;
                    }
                }
            }

            if (resolved.isEmpty() && singleEpisodeFolder) {
                resolved = anibridgeResolutionService.resolveEpisode(safeSegments.get(), 1);
            }

            if (resolved.isEmpty()) {
                if (offsetNeededButIneligible) {
                    ambiguousAbsoluteNumbering = true;
                }
                continue;
            }

            final String thumbnailPath = stillPathOf(
                    anime.tmdbId,
                    resolved.get(),
                    episodesBySeason,
                    folderId,
                    folderName
            );

            animeTmdbFilePairingService.updateTmdbFilePairing(
                    videoFile.id,
                    new TmdbFilePairingRequest()
                            .seasonNumber(resolved.get().seasonNumber())
                            .episodeNumber(resolved.get().tmdbEpisode())
                            .thumbnailPath(thumbnailPath));

            paired++;
        }

        if (ambiguousAbsoluteNumbering) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Numeração absoluta detectada, mas o mapeamento do anibridge não cobre a temporada "
                            + "isolada com segurança — os arquivos que dependiam dessa correção não "
                            + "foram pareados",
                    folderId,
                    null);
        }

        if (paired < videoFiles.size()) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "%d de %d arquivos pareados".formatted(paired, videoFiles.size()),
                    folderId,
                    null);
        }

        final AnimeReviewStatus reviewStatus = paired == videoFiles.size()
                ? AnimeReviewStatus.PENDING_REVIEW
                : AnimeReviewStatus.PARTIAL;

        animeService.updateReviewStatusIfNotReviewed(anime.anilistId, reviewStatus);
    }

    /**
     * Busca o {@code stillPath} (thumbnail padrão do TMDB) do episódio resolvido. Cacheia a
     * temporada inteira por pasta — evita uma chamada por arquivo quando vários episódios caem
     * na mesma temporada. Falha aqui nunca aborta o pairing — sem thumbnail, o arquivo ainda é
     * pareado, só sem imagem (igual um pairing manual que não preencheu esse campo).
     */
    private String stillPathOf(Integer tmdbId,
                               ResolvedEpisode resolved,
                               Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason,
                               Integer folderId,
                               String folderName) {

        if (resolved.seasonNumber() == null) {
            return null;
        }

        final List<TmdbEpisodeResponse> episodes = episodesBySeason.computeIfAbsent(resolved.seasonNumber(), seasonNumber -> {
            try {
                return RetryUtils.withRateLimitRetry(
                        () -> tmdbSearchService.getSeason(
                                tmdbId,
                                seasonNumber,
                                false
                        ).episodes());

            } catch (IntegrationException | ResourceNotFoundException e) {
                Log.warnf(e, "Busca da temporada %d no TMDB falhou pra pasta %d (%s)", seasonNumber, folderId, folderName);
                return List.of();
            }
        });

        return episodes.stream()
                .filter(episode -> episode.episodeNumber().equals(resolved.tmdbEpisode()))
                .findFirst()
                .map(TmdbEpisodeResponse::stillPath)
                .orElse(null);
    }

}
