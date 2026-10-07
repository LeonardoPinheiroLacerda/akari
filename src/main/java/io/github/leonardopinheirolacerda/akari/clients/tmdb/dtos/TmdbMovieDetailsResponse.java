package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de {@code GET /3/movie/{id}} — reflete integralmente o body devolvido pelo TMDB v3.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieDetailsResponse(
        Boolean adult,
        @JsonProperty("backdrop_path") String backdropPath,
        Long budget,
        List<TmdbGenreResponse> genres,
        String homepage,
        Integer id,
        @JsonProperty("imdb_id") String imdbId,
        @JsonProperty("origin_country") List<String> originCountry,
        @JsonProperty("original_language") String originalLanguage,
        @JsonProperty("original_title") String originalTitle,
        String overview,
        Double popularity,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("production_companies") List<TmdbProductionCompanyResponse> productionCompanies,
        @JsonProperty("production_countries") List<TmdbProductionCountryResponse> productionCountries,
        @JsonProperty("release_date") String releaseDate,
        Long revenue,
        Integer runtime,
        @JsonProperty("spoken_languages") List<TmdbSpokenLanguageResponse> spokenLanguages,
        String status,
        String tagline,
        String title,
        Boolean video,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount
) {
}
