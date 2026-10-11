package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonSummaryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbSearchService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.Set;
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
    IngestionTmdbMappingService ingestionTmdbMappingService;

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

        if (boundAnime == null || boundAnime.tmdbMediaType != TmdbMediaType.TV) {
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

        final Optional<TmdbMappingCandidate> candidate = ingestionTmdbMappingService.resolveBestCandidate(
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

        } catch (IntegrationException e) {
            Log.warnf(e, "Bind TMDB %d falhou pro anime %d (pasta %d)", best.tmdbId(), anime.anilistId, folderId);
            notificationService.notify(
                    NotificationLevel.WARNING, folderName, "Bind no TMDB falhou", folderId, null);
            return anime;
        }

        return animeService.findByFolder(folderId)
                .orElse(anime);
    }

    private void pairEpisodes(Integer folderId, String folderName, Anime anime) {

        final Optional<TmdbMappingCandidate> candidate = ingestionTmdbMappingService.resolveBestCandidate(
                anime.anilistId,
                anime.malId,
                anime.episodes
        );

        if (candidate.isEmpty()) {
            return;
        }

        final Optional<List<MappingSegment>> safeSegments = resolveSafeSegments(
                candidate.get(),
                folderId,
                folderName
        );

        if (safeSegments.isEmpty()) {
            notificationService.notify(
                    NotificationLevel.WARNING,
                    folderName,
                    "Mapeamento do anibridge tem temporadas conflitantes pra esse anime — "
                            + "pareamento automático não tentado",
                    folderId,
                    null);
            return;
        }

        final List<VideoFile> videoFiles = videoFileService.listByFolder(folderId);
        final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();

        final int absoluteOffset = absoluteEpisodeOffsetService.computeOffset(anime.anilistId);

        final Optional<List<MappingSegment>> offsetSegments = absoluteOffset > 0
                ? ingestionTmdbMappingService.restrictToFullSeasonCoverage(safeSegments.get(), anime.episodes)
                : Optional.empty();

        int paired = 0;
        boolean ambiguousAbsoluteNumbering = false;

        for (VideoFile videoFile : videoFiles) {
            final OptionalInt episodeNumber = EpisodeNumberExtractor.extract(videoFile.name);

            if (episodeNumber.isEmpty()) {
                continue;
            }

            Optional<ResolvedEpisode> resolved = ingestionTmdbMappingService.resolveEpisode(
                    safeSegments.get(),
                    episodeNumber.getAsInt()
            );

            if (resolved.isEmpty() && absoluteOffset > 0) {
                if (offsetSegments.isPresent()) {
                    resolved = ingestionTmdbMappingService.resolveEpisode(
                            offsetSegments.get(),
                            episodeNumber.getAsInt() - absoluteOffset
                    );
                } else {
                    ambiguousAbsoluteNumbering = true;
                }
            }

            if (resolved.isEmpty()) {
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
    }

    /**
     * Resolve os segments seguros pra usar no pairing. Sem sobreposição de range entre eles
     * ({@link IngestionTmdbMappingService#hasOverlap}), usa a lista crua direto — caso comum,
     * sem ambiguidade, sem precisar confirmar temporada real no TMDB. Só paga essa chamada
     * extra quando existe conflito de fato pra resolver, tentando descartar o(s) segment(s)
     * residual(is)/errado(s) do anibridge que apontam pra uma temporada que o show nem tem mais.
     * Só pra {@code TV}; filme não tem a noção de "temporada" que a validação resolve.
     */
    private Optional<List<MappingSegment>> resolveSafeSegments(TmdbMappingCandidate candidate,
                                                               Integer folderId,
                                                               String folderName) {

        final List<MappingSegment> rawSegments = candidate.segments();

        if (!ingestionTmdbMappingService.hasOverlap(rawSegments)) {
            return Optional.of(rawSegments);
        }

        if (candidate.mediaType() != TmdbMediaType.TV) {
            return Optional.empty();
        }

        final Set<Integer> existingSeasonNumbers;

        try {
            existingSeasonNumbers = RetryUtils.withRateLimitRetry(() -> tmdbSearchService
                    .getTvDetails(candidate.tmdbId(), false)
                    .seasons()
                    .stream()
                    .map(TmdbSeasonSummaryResponse::seasonNumber)
                    .collect(Collectors.toSet()));

        } catch (IntegrationException e) {
            Log.warnf(e, "Busca das temporadas do TMDB %d falhou pra pasta %d (%s)", candidate.tmdbId(), folderId, folderName);
            return Optional.empty();
        }

        final List<MappingSegment> validSegments = rawSegments.stream()
                .filter(segment -> existingSeasonNumbers.contains(segment.seasonNumber))
                .toList();

        if (validSegments.isEmpty() || ingestionTmdbMappingService.hasOverlap(validSegments)) {
            return Optional.empty();
        }

        return Optional.of(validSegments);
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

            } catch (IntegrationException e) {
                Log.warnf(e, "Busca da temporada %d no TMDB falhou pra pasta %d (%s)", seasonNumber, folderId, folderName);
                return List.of();
            }
        });

        return episodes.stream()
                .filter(episode -> episode.episodeNumber().equals(resolved.tmdbEpisode()))
                .map(TmdbEpisodeResponse::stillPath)
                .findFirst()
                .orElse(null);
    }

}
