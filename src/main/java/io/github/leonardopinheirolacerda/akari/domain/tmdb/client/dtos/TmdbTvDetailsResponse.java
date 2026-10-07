package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de {@code GET /3/tv/{id}} — reflete integralmente o body devolvido pelo TMDB v3.
 *
 * <p>O array {@code seasons} vem como sumário ({@link TmdbSeasonSummaryResponse}) — para
 * episódios completos é preciso uma chamada adicional a {@code /3/tv/{id}/season/{n}} por
 * temporada.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTvDetailsResponse(
        Boolean adult,
        @JsonProperty("backdrop_path") String backdropPath,
        @JsonProperty("created_by") List<TmdbCreatedByResponse> createdBy,
        @JsonProperty("episode_run_time") List<Integer> episodeRunTime,
        @JsonProperty("first_air_date") String firstAirDate,
        List<TmdbGenreResponse> genres,
        String homepage,
        Integer id,
        @JsonProperty("in_production") Boolean inProduction,
        List<String> languages,
        @JsonProperty("last_air_date") String lastAirDate,
        @JsonProperty("last_episode_to_air") TmdbEpisodeSummaryResponse lastEpisodeToAir,
        String name,
        @JsonProperty("next_episode_to_air") TmdbEpisodeSummaryResponse nextEpisodeToAir,
        List<TmdbNetworkResponse> networks,
        @JsonProperty("number_of_episodes") Integer numberOfEpisodes,
        @JsonProperty("number_of_seasons") Integer numberOfSeasons,
        @JsonProperty("origin_country") List<String> originCountry,
        @JsonProperty("original_language") String originalLanguage,
        @JsonProperty("original_name") String originalName,
        String overview,
        Double popularity,
        @JsonProperty("poster_path") String posterPath,
        @JsonProperty("production_companies") List<TmdbProductionCompanyResponse> productionCompanies,
        @JsonProperty("production_countries") List<TmdbProductionCountryResponse> productionCountries,
        List<TmdbSeasonSummaryResponse> seasons,
        @JsonProperty("spoken_languages") List<TmdbSpokenLanguageResponse> spokenLanguages,
        String status,
        String tagline,
        String type,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount
) {
}
