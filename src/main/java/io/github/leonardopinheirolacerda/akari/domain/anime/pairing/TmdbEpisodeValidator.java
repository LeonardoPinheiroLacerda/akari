package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FuzzyDate;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
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
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    /**
     * Confirma o episódio do arquivo no TMDB e monta o pairing.
     *
     * @param anime anime com binding TMDB de série
     * @param videoFile arquivo resolvido
     * @param resolved temporada/episódio a que a conversão chegou
     * @param seasonEpisodes temporadas da série, buscadas uma vez por pasta
     * @return {@code PAIRED} com o pairing; {@code NOT_FOUND_IN_TMDB} se o episódio não existe
     *         no TMDB; ou {@code OUT_OF_AIR_WINDOW} se ele foi ao ar a mais de
     *         {@link #AIR_WINDOW_TOLERANCE_DAYS} dias antes da estreia ou depois do fim do anime
     */
    public FilePairingResolution validate(Anime anime,
                                          VideoFile videoFile,
                                          ResolvedEpisode resolved,
                                          TmdbSeasonEpisodes seasonEpisodes) {

        final Optional<TmdbEpisodeResponse> episode = findEpisode(resolved, seasonEpisodes);

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
     * Episódio resolvido no TMDB, de onde sai a metadata do pairing (thumbnail, nome, sinopse
     * etc.). Vazio se a temporada não existir no TMDB (o anibridge pode estar dessincronizado
     * dele), se a busca falhar ou se o episódio não estiver na temporada — nesses casos o arquivo
     * sai como {@code NOT_FOUND_IN_TMDB}, sem pairing, pra aparecer como pendente em vez de
     * pareado sem metadata.
     */
    private Optional<TmdbEpisodeResponse> findEpisode(ResolvedEpisode resolved,
                                                      TmdbSeasonEpisodes seasonEpisodes) {

        if (resolved.seasonNumber() == null) {
            return Optional.empty();
        }

        return seasonEpisodes.of(resolved.seasonNumber())
                .stream()
                .filter(episode -> episode.episodeNumber().equals(resolved.tmdbEpisode()))
                .findFirst();
    }

    /**
     * Confere se o episódio do TMDB foi ao ar dentro do período de exibição do anime na AniList,
     * com {@link #AIR_WINDOW_TOLERANCE_DAYS} dias de folga em cada ponta. Data parcial da AniList
     * vale pelo período inteiro que ela cobre (só o ano: do início ao fim do ano). Sem data de
     * fim (anime em exibição), só o início limita; sem data do episódio no TMDB, ou com a do
     * anime sem ano, não há o que comparar e o episódio é aceito.
     */
    private boolean isWithinAirWindow(Anime anime, TmdbEpisodeResponse episode) {
        final Optional<LocalDate> airDate = AirDates.parse(episode.airDate());

        if (airDate.isEmpty()) {
            return true;
        }

        final FuzzyDate startDate = anime.startDate;

        if (startDate != null && startDate.year != null) {
            final LocalDate windowStart = AirDates.firstDayOf(startDate)
                    .minusDays(AIR_WINDOW_TOLERANCE_DAYS);

            if (airDate.get().isBefore(windowStart)) {
                return false;
            }
        }

        final FuzzyDate endDate = anime.endDate;

        if (endDate == null || endDate.year == null) {
            return true;
        }

        final LocalDate windowEnd = AirDates.lastDayOf(endDate).plusDays(AIR_WINDOW_TOLERANCE_DAYS);

        return !airDate.get().isAfter(windowEnd);
    }

}
