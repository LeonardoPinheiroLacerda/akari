package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Episódio individual dentro de uma temporada, retornado por
 * {@code GET /3/tv/{id}/season/{n}}. Só aparece em respostas de séries.
 *
 * @param tmdbId identificador do episódio no TMDB
 * @param seasonNumber número da temporada
 * @param episodeNumber número do episódio dentro da temporada
 * @param episodeType classificação do TMDB ({@code "standard"}, {@code "finale"}, ...)
 * @param name nome do episódio
 * @param overview sinopse
 * @param airDate data de exibição em ISO-8601
 * @param stillPath path da thumbnail do episódio
 * @param runtime duração em minutos
 * @param productionCode código de produção do episódio
 * @param showId id da série mãe
 * @param voteAverage nota média
 * @param voteCount total de votos
 * @param crew equipe de produção do episódio
 * @param guestStars atores convidados
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbEpisodeResponse(
        @JsonProperty("id") Integer tmdbId,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("episode_type") String episodeType,
        String name,
        String overview,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("still_path") String stillPath,
        Integer runtime,
        @JsonProperty("production_code") String productionCode,
        @JsonProperty("show_id") Integer showId,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount,
        List<TmdbCrewMemberResponse> crew,
        @JsonProperty("guest_stars") List<TmdbGuestStarResponse> guestStars
) {
}
