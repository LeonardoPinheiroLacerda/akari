package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Sumário de episódio usado em {@code lastEpisodeToAir}/{@code nextEpisodeToAir} dentro do
 * detalhe de uma série. Não confundir com {@link TmdbEpisodeResponse}, que representa o
 * episódio completo dentro de uma temporada.
 *
 * @param tmdbId id do episódio no TMDB
 * @param name nome do episódio
 * @param overview sinopse
 * @param voteAverage nota média
 * @param voteCount total de votos
 * @param airDate data de exibição em ISO-8601
 * @param episodeNumber número dentro da temporada
 * @param episodeType classificação do TMDB ({@code "standard"}, {@code "finale"}, ...)
 * @param productionCode código de produção
 * @param runtime duração em minutos
 * @param seasonNumber número da temporada
 * @param showId id da série mãe
 * @param stillPath path da thumbnail
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbEpisodeSummaryResponse(
        @JsonProperty("id") Integer tmdbId,
        String name,
        String overview,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount,
        @JsonProperty("air_date") String airDate,
        @JsonProperty("episode_number") Integer episodeNumber,
        @JsonProperty("episode_type") String episodeType,
        @JsonProperty("production_code") String productionCode,
        Integer runtime,
        @JsonProperty("season_number") Integer seasonNumber,
        @JsonProperty("show_id") Integer showId,
        @JsonProperty("still_path") String stillPath
) {
}
