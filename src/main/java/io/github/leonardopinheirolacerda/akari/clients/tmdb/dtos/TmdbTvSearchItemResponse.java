package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Item bruto de {@code GET /3/search/tv} — sempre uma série.
 *
 * @param id id da série no TMDB
 * @param adult flag de conteúdo adulto
 * @param popularity métrica de popularidade
 * @param overview sinopse
 * @param posterPath path relativo do poster
 * @param backdropPath path relativo do backdrop
 * @param originalLanguage idioma original em ISO 639-1
 * @param voteAverage nota média (0–10)
 * @param voteCount total de votos
 * @param genreIds ids de gêneros (não expandidos)
 * @param name nome da série
 * @param originalName nome no idioma original
 * @param firstAirDate data de estreia em ISO-8601
 * @param originCountry países de origem
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvSearchItemResponse(
        Integer id,
        Boolean adult,
        Double popularity,
        String overview,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("original_language") String originalLanguage,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount,
        @JsonProperty("genre_ids") List<Integer> genreIds,
        String name,
        @JsonProperty("original_name") String originalName,
        @JsonProperty("first_air_date") String firstAirDate,
        @JsonProperty("origin_country") List<String> originCountry
) {
}
