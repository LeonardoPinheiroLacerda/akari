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
 *
 * @param adult se é conteúdo adulto
 * @param backdropPath path relativo da imagem de fundo
 * @param createdBy criadores da série
 * @param episodeRunTime durações típicas de episódio em minutos
 * @param firstAirDate data de estreia em ISO-8601
 * @param genres gêneros da série
 * @param homepage site oficial
 * @param id id da série no TMDB
 * @param inProduction se a série ainda está em produção
 * @param languages idiomas falados na série (códigos ISO 639-1)
 * @param lastAirDate data do último episódio exibido em ISO-8601
 * @param lastEpisodeToAir sumário do último episódio já exibido
 * @param name nome no idioma solicitado
 * @param nextEpisodeToAir sumário do próximo episódio a exibir; {@code null} se não há previsão
 * @param networks redes que exibem a série
 * @param numberOfEpisodes total de episódios já lançados
 * @param numberOfSeasons total de temporadas já lançadas
 * @param originCountry países de origem (códigos ISO 3166-1)
 * @param originalLanguage idioma original (código ISO 639-1)
 * @param originalName nome no idioma original
 * @param overview sinopse
 * @param popularity métrica de popularidade do TMDB
 * @param posterPath path relativo do poster
 * @param productionCompanies produtoras envolvidas
 * @param productionCountries países de produção
 * @param seasons sumário de cada temporada (sem os episódios)
 * @param spokenLanguages idiomas falados na série
 * @param status status de produção (ex.: {@code "Ended"}, {@code "Returning Series"})
 * @param tagline frase de efeito da série
 * @param type tipo de série no TMDB (ex.: {@code "Scripted"})
 * @param voteAverage nota média dos usuários do TMDB
 * @param voteCount total de votos
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
