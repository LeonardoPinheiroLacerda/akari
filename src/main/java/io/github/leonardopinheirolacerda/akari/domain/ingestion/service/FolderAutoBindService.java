package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeReviewStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeOverrideService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.EpisodePairingResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

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
    AnimeOverrideService animeOverrideService;

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    AnimeTmdbFilePairingService animeTmdbFilePairingService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    EpisodePairingResolutionService episodePairingResolutionService;

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
                    coverImageOf(animeService.findByFolder(folderId).orElse(null)));
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
                    coverImageOf(anime));
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
                    NotificationLevel.WARNING, folderName, "Bind no TMDB falhou", folderId, coverImageOf(anime));
            return anime;
        }

        return animeService.findByFolder(folderId)
                .orElse(anime);
    }

    private void pairEpisodes(Integer folderId, String folderName, Anime anime) {
        final List<FilePairingResolution> resolutions = episodePairingResolutionService.resolve(
                anime,
                folderId,
                folderName
        );

        if (hasStatus(resolutions, TmdbFilePairingProposalStatus.NO_MAPPING)) {
            animeService.updateReviewStatusIfNotReviewed(anime.anilistId, AnimeReviewStatus.PARTIAL);
            return;
        }

        if (hasStatus(resolutions, TmdbFilePairingProposalStatus.CONFLICTING_MAPPING)) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Mapeamento do anibridge tem temporadas conflitantes pra esse anime — "
                            + "pareamento automático não tentado",
                    folderId,
                    coverImageOf(anime));
            animeService.updateReviewStatusIfNotReviewed(anime.anilistId, AnimeReviewStatus.PARTIAL);
            return;
        }

        int paired = 0;

        for (FilePairingResolution resolution : resolutions) {
            if (resolution.status() != TmdbFilePairingProposalStatus.PAIRED) {
                continue;
            }

            animeTmdbFilePairingService.updateTmdbFilePairing(resolution.videoFile().id, resolution.pairing());

            paired++;
        }

        if (hasStatus(resolutions, TmdbFilePairingProposalStatus.AMBIGUOUS_ABSOLUTE_NUMBERING)) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Numeração absoluta detectada, mas o mapeamento do anibridge não cobre a temporada "
                            + "isolada com segurança — os arquivos que dependiam dessa correção não "
                            + "foram pareados",
                    folderId,
                    coverImageOf(anime));
        }

        final List<FilePairingResolution> missingInTmdb = resolutions.stream()
                .filter(resolution -> resolution.status() == TmdbFilePairingProposalStatus.NOT_FOUND_IN_TMDB)
                .toList();

        if (!missingInTmdb.isEmpty()) {
            final String seasons = missingInTmdb.stream()
                    .map(resolution -> resolution.resolvedEpisode().seasonNumber())
                    .filter(Objects::nonNull)
                    .distinct()
                    .sorted()
                    .map(String::valueOf)
                    .collect(Collectors.joining(", "));

            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "%d arquivos não foram pareados: o mapeamento do anibridge aponta pra episódios que não existem no TMDB %d (temporada %s)"
                            .formatted(missingInTmdb.size(), anime.tmdbId, seasons),
                    folderId,
                    coverImageOf(anime));
        }

        if (paired < resolutions.size()) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "%d de %d arquivos pareados".formatted(paired, resolutions.size()),
                    folderId,
                    coverImageOf(anime));
        }

        final AnimeReviewStatus reviewStatus = paired == resolutions.size()
                ? AnimeReviewStatus.PENDING_REVIEW
                : AnimeReviewStatus.PARTIAL;

        animeService.updateReviewStatusIfNotReviewed(anime.anilistId, reviewStatus);
    }

    private boolean hasStatus(List<FilePairingResolution> resolutions, TmdbFilePairingProposalStatus status) {
        return resolutions.stream()
                .anyMatch(resolution -> resolution.status() == status);
    }

    /**
     * Capa pra notificação relacionada a um anime já bindado — override do TMDB se setado,
     * senão a capa da AniList (ver {@link AnimeOverrideService#resolveThumbnailMedium}).
     *
     * @param anime anime da pasta, ou {@code null} se ainda não há nenhum bindado
     * @return a URL da capa, ou {@code null} sem anime
     */
    private String coverImageOf(Anime anime) {
        return anime == null
                ? null
                : animeOverrideService.resolveThumbnailMedium(anime);
    }

}
