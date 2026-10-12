package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de {@code GET /3/movie/{id}} — reflete integralmente o body devolvido pelo TMDB v3.
 *
 * @param adult se é conteúdo adulto
 * @param backdropPath path relativo da imagem de fundo
 * @param budget orçamento de produção em dólares
 * @param genres gêneros do filme
 * @param homepage site oficial
 * @param id id do filme no TMDB
 * @param imdbId id correspondente no IMDb
 * @param originCountry países de origem (códigos ISO 3166-1)
 * @param originalLanguage idioma original (código ISO 639-1)
 * @param originalTitle título no idioma original
 * @param overview sinopse
 * @param popularity métrica de popularidade do TMDB
 * @param posterPath path relativo do poster
 * @param productionCompanies produtoras envolvidas
 * @param productionCountries países de produção
 * @param releaseDate data de lançamento em ISO-8601
 * @param revenue bilheteria em dólares
 * @param runtime duração em minutos
 * @param spokenLanguages idiomas falados no filme
 * @param status status de lançamento (ex.: {@code "Released"})
 * @param tagline frase de efeito do filme
 * @param title título no idioma solicitado
 * @param video se é um vídeo (não um filme de verdade) — campo do TMDB, quase sempre {@code false}
 * @param voteAverage nota média dos usuários do TMDB
 * @param voteCount total de votos
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
