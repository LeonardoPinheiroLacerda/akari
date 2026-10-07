package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de {@code GET /3/tv/{id}/season/{n}}.
 *
 * @param objectId id interno do Mongo do TMDB ({@code _id})
 * @param airDate data de estreia da temporada
 * @param episodes episódios da temporada
 * @param name nome da temporada
 * @param overview sinopse
 * @param id id da temporada no TMDB
 * @param networks redes que exibem a série
 * @param posterPath path relativo do poster
 * @param seasonNumber número da temporada (0 = specials)
 * @param voteAverage nota média
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSeasonResponse(
        @JsonProperty("_id") String objectId,
        @JsonProperty("air_date") String airDate,
        List<TmdbEpisodeResponse> episodes,
        String name,
        String overview,
        Integer id,
        List<TmdbNetworkResponse> networks,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("vote_average") Double voteAverage
) {
}
