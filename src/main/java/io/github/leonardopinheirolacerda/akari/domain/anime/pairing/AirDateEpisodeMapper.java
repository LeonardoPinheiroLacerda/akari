package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Segunda conversão da resolução do pairing, usada só no arquivo que a conversão pelo anibridge
 * ({@link AnibridgeEpisodeMapper}) não pareou: prepara, por pasta, os episódios do TMDB exibidos
 * no período do anime na AniList, em ordem de exibição ({@link AirDateFolderMapping}). Não depende
 * do anibridge — cobre a série longa que ele só mapeia no começo (ex.: Gintama, mapeado só na
 * primeira temporada do TMDB) e o mapeamento que aponta pra temporada errada.
 *
 * <p>O período vai da estreia ao fim do anime, com {@link #AIR_DATE_MARGIN_DAYS} dias de folga em
 * cada ponta; em anime em exibição ({@code RELEASING}), da estreia até hoje. Precisa das datas
 * completas, com dia: uma data parcial (só ano ou mês) cobriria episódios de outra temporada.
 * Anime terminado só é convertido se a quantidade de episódios exibidos no período for igual ao
 * total do anime na AniList — a trava que impede casar episódios por coincidência.
 */
@ApplicationScoped
public class AirDateEpisodeMapper {

    // Folga nas pontas do período, pra diferença de fuso e de data de estreia entre a AniList e o
    // TMDB — menor que o intervalo semanal entre episódios, pra não puxar o episódio vizinho.
    private static final int AIR_DATE_MARGIN_DAYS = 3;

    private static final String RELEASING_STATUS = "RELEASING";

    /**
     * Prepara a conversão pelo período de exibição dos arquivos de uma pasta.
     *
     * @param anime anime com binding TMDB de série
     * @param seasonEpisodes temporadas da série, buscadas uma vez por pasta
     * @return a conversão da pasta; vazia se o período do anime não for conhecido com dia, ou,
     *         em anime terminado, se a quantidade de episódios exibidos nele não bater com o
     *         total do anime
     */
    public AirDateFolderMapping prepare(Anime anime, TmdbSeasonEpisodes seasonEpisodes) {
        final boolean releasing = RELEASING_STATUS.equals(anime.status);

        if (!AirDates.isComplete(anime.startDate)) {
            return AirDateFolderMapping.empty();
        }

        if (!releasing && (!AirDates.isComplete(anime.endDate) || anime.episodes == null)) {
            return AirDateFolderMapping.empty();
        }

        final LocalDate windowStart = AirDates.firstDayOf(anime.startDate)
                .minusDays(AIR_DATE_MARGIN_DAYS);

        final LocalDate windowEnd = releasing
                ? LocalDate.now()
                : AirDates.lastDayOf(anime.endDate).plusDays(AIR_DATE_MARGIN_DAYS);

        final List<ResolvedEpisode> airedEpisodes = seasonEpisodes.regularSeasonNumbers()
                .stream()
                .flatMap(seasonNumber -> seasonEpisodes.of(seasonNumber).stream())
                .filter(episode -> isAiredWithin(episode, windowStart, windowEnd))
                .sorted(Comparator
                        .comparing(TmdbEpisodeResponse::airDate)
                        .thenComparing(TmdbEpisodeResponse::seasonNumber)
                        .thenComparing(TmdbEpisodeResponse::episodeNumber))
                .map(episode -> new ResolvedEpisode(
                        episode.seasonNumber(),
                        episode.episodeNumber()
                ))
                .toList();

        if (!releasing && airedEpisodes.size() != anime.episodes) {
            return AirDateFolderMapping.empty();
        }

        return AirDateFolderMapping.of(airedEpisodes);
    }

    private static boolean isAiredWithin(TmdbEpisodeResponse episode,
                                         LocalDate windowStart,
                                         LocalDate windowEnd) {

        return AirDates.parse(episode.airDate())
                .filter(airDate -> !airDate.isBefore(windowStart) && !airDate.isAfter(windowEnd))
                .isPresent();
    }

}
