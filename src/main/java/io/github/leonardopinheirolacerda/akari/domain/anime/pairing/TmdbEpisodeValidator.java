package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FuzzyDate;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Última etapa da resolução do pairing: confirma no TMDB o episódio a que a conversão chegou e
 * monta o pairing dele. O episódio precisa existir no TMDB, de onde sai a metadata do pairing, e
 * ter ido ao ar perto do período de exibição do anime na AniList
 * ({@link #AIR_WINDOW_TOLERANCE_DAYS}).
 */
@ApplicationScoped
public class TmdbEpisodeValidator {

    // Folga, antes da estreia e depois do fim do anime na AniList, pra data do episódio no TMDB —
    // absorve exibição em streaming, DVD e especiais lançados longe da exibição na TV, e ainda
    // recusa o episódio de outra temporada ou de outro anime da franquia.
    private static final int AIR_WINDOW_TOLERANCE_DAYS = 365;

    @Inject
    TmdbService tmdbService;

    @Inject
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    /**
     * Confirma o episódio do arquivo no TMDB e monta o pairing.
     *
     * @param anime anime com binding TMDB de série
     * @param videoFile arquivo resolvido
     * @param resolved temporada/episódio a que a conversão chegou
     * @param episodesBySeason episódios já buscados no TMDB, por temporada — um mapa por pasta,
     *                         preenchido aqui, pra não buscar a mesma temporada a cada arquivo
     * @param folderId pasta do anime, só pra log
     * @param folderName nome da pasta, só pra log
     * @return {@code PAIRED} com o pairing; {@code NOT_FOUND_IN_TMDB} se o episódio não existe
     *         no TMDB; ou {@code OUT_OF_AIR_WINDOW} se ele foi ao ar a mais de
     *         {@link #AIR_WINDOW_TOLERANCE_DAYS} dias antes da estreia ou depois do fim do anime
     */
    public FilePairingResolution validate(Anime anime,
                                          VideoFile videoFile,
                                          ResolvedEpisode resolved,
                                          Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason,
                                          Integer folderId,
                                          String folderName) {

        final Optional<TmdbEpisodeResponse> episode = findEpisode(
                anime.tmdbId,
                resolved,
                episodesBySeason,
                folderId,
                folderName
        );

        if (episode.isEmpty()) {
            return new FilePairingResolution(
                    videoFile,
                    TmdbFilePairingProposalStatus.NOT_FOUND_IN_TMDB,
                    resolved,
                    null,
                    null
            );
        }

        if (!isWithinAirWindow(anime, episode.get())) {
            return new FilePairingResolution(
                    videoFile,
                    TmdbFilePairingProposalStatus.OUT_OF_AIR_WINDOW,
                    resolved,
                    null,
                    null
            );
        }

        final TmdbFilePairingRequest pairing = animeTmdbFilePairingMapper.toPairingRequest(
                episode.get()
        );

        return new FilePairingResolution(
                videoFile,
                TmdbFilePairingProposalStatus.PAIRED,
                resolved,
                pairing,
                null
        );
    }

    /**
     * Busca no TMDB o episódio resolvido, de onde sai a metadata do pairing (thumbnail, nome,
     * sinopse etc.). Cacheia a temporada inteira por pasta — evita uma chamada por arquivo quando
     * vários episódios caem na mesma temporada. Vazio se a temporada não existir no TMDB (o
     * anibridge pode estar dessincronizado dele), se a busca falhar ou se o episódio não estiver
     * na temporada — nesses casos o arquivo sai como {@code NOT_FOUND_IN_TMDB}, sem pairing, pra
     * aparecer como pendente em vez de pareado sem metadata.
     */
    private Optional<TmdbEpisodeResponse> findEpisode(
            Integer tmdbId,
            ResolvedEpisode resolved,
            Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason,
            Integer folderId,
            String folderName) {

        if (resolved.seasonNumber() == null) {
            return Optional.empty();
        }

        final List<TmdbEpisodeResponse> episodes = episodesBySeason.computeIfAbsent(
                resolved.seasonNumber(),
                seasonNumber -> fetchSeasonEpisodes(tmdbId, seasonNumber, folderId, folderName)
        );

        return episodes.stream()
                .filter(episode -> episode.episodeNumber().equals(resolved.tmdbEpisode()))
                .findFirst();
    }

    private List<TmdbEpisodeResponse> fetchSeasonEpisodes(Integer tmdbId,
                                                          Integer seasonNumber,
                                                          Integer folderId,
                                                          String folderName) {

        try {
            return RetryUtils.withRateLimitRetry(
                    () -> tmdbService.getSeasonEpisodes(tmdbId, seasonNumber)
            );
        } catch (IntegrationException e) {
            Log.warnf(
                    e,
                    "Busca da temporada %d no TMDB falhou pra pasta %d (%s)",
                    seasonNumber,
                    folderId,
                    folderName
            );
            return List.of();
        }
    }

    /**
     * Confere se o episódio do TMDB foi ao ar dentro do período de exibição do anime na AniList,
     * com {@link #AIR_WINDOW_TOLERANCE_DAYS} dias de folga em cada ponta. Data parcial da AniList
     * vale pelo período inteiro que ela cobre (só o ano: do início ao fim do ano). Sem data de
     * fim (anime em exibição), só o início limita; sem data do episódio no TMDB, ou com a do
     * anime sem ano, não há o que comparar e o episódio é aceito.
     */
    private boolean isWithinAirWindow(Anime anime, TmdbEpisodeResponse episode) {
        final Optional<LocalDate> airDate = parseAirDate(episode.airDate());

        if (airDate.isEmpty()) {
            return true;
        }

        final FuzzyDate startDate = anime.startDate;

        if (startDate != null && startDate.year != null) {
            final LocalDate windowStart = firstDayOf(startDate)
                    .minusDays(AIR_WINDOW_TOLERANCE_DAYS);

            if (airDate.get().isBefore(windowStart)) {
                return false;
            }
        }

        final FuzzyDate endDate = anime.endDate;

        if (endDate == null || endDate.year == null) {
            return true;
        }

        final LocalDate windowEnd = lastDayOf(endDate).plusDays(AIR_WINDOW_TOLERANCE_DAYS);

        return !airDate.get().isAfter(windowEnd);
    }

    private static Optional<LocalDate> parseAirDate(String airDate) {
        if (airDate == null || airDate.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(LocalDate.parse(airDate));
        } catch (DateTimeParseException e) {
            Log.warnf("Data de exibição do TMDB fora do formato ISO: %s", airDate);
            return Optional.empty();
        }
    }

    private static LocalDate firstDayOf(FuzzyDate date) {
        if (date.month == null) {
            return LocalDate.of(date.year, 1, 1);
        }

        if (date.day == null) {
            return LocalDate.of(date.year, date.month, 1);
        }

        return LocalDate.of(date.year, date.month, date.day);
    }

    private static LocalDate lastDayOf(FuzzyDate date) {
        if (date.month == null) {
            return LocalDate.of(date.year, 12, 31);
        }

        if (date.day == null) {
            return YearMonth.of(date.year, date.month)
                    .atEndOfMonth();
        }

        return LocalDate.of(date.year, date.month, date.day);
    }

}
