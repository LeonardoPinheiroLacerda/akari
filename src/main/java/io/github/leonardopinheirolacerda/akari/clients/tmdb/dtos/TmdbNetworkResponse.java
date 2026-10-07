package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Rede de TV/streaming que exibe uma série.
 *
 * @param tmdbId identificador da rede no TMDB
 * @param logoPath path relativo do logo
 * @param name nome da rede
 * @param originCountry país de origem em ISO 3166-1
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbNetworkResponse(
        @JsonProperty("id") Integer tmdbId,
        @JsonProperty("logo_path") String logoPath,
        String name,
        @JsonProperty("origin_country") String originCountry
) {
}
