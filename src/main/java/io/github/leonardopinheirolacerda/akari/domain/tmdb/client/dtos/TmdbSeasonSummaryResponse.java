package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sumário de temporada retornado no detalhe de uma série ({@code GET /3/tv/{id}}) — sem
 * episódios detalhados; esses ficam em {@link TmdbSeasonResponse#episodes()}.
 *
 * @param airDate data de estreia da temporada (ISO-8601)
 * @param episodeCount total de episódios
 * @param tmdbId id da temporada no TMDB
 * @param name nome da temporada
 * @param overview sinopse
 * @param posterPath path relativo do poster
 * @param seasonNumber número da temporada (0 = specials)
 * @param voteAverage nota média
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonSummaryResponse(
        @JsonProperty("air_date") String airDate,
        @JsonProperty("episode_count") Integer episodeCount,
        @JsonProperty("id") Integer tmdbId,
        String name,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("vote_average") Double voteAverage
) {
}
