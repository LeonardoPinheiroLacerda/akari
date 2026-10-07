package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Item bruto de {@code GET /3/search/movie} — sempre um filme.
 *
 * @param id id do filme no TMDB
 * @param adult flag de conteúdo adulto
 * @param popularity métrica de popularidade
 * @param overview sinopse
 * @param posterPath path relativo do poster
 * @param backdropPath path relativo do backdrop
 * @param originalLanguage idioma original em ISO 639-1
 * @param voteAverage nota média (0–10)
 * @param voteCount total de votos
 * @param genreIds ids de gêneros (não expandidos)
 * @param title título
 * @param originalTitle título no idioma original
 * @param releaseDate data de lançamento em ISO-8601
 * @param video flag indicando vídeo direto
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieSearchItemResponse(
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
        String title,
        @JsonProperty("original_title") String originalTitle,
        @JsonProperty("release_date") String releaseDate,
        Boolean video
) {
}
