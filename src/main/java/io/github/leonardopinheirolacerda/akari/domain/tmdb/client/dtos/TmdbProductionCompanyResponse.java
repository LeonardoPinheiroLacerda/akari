package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Estúdio/produtora associada a uma obra no TMDB.
 *
 * @param tmdbId identificador da produtora no TMDB
 * @param logoPath path relativo do logo
 * @param name nome da produtora
 * @param originCountry país de origem em ISO 3166-1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProductionCompanyResponse(
        @JsonProperty("id") Integer tmdbId,
        @JsonProperty("logo_path") String logoPath,
        String name,
        @JsonProperty("origin_country") String originCountry
) {
}
