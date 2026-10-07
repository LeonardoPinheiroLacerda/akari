package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * País de produção associado a uma obra no TMDB.
 *
 * @param iso31661 código do país (ISO 3166-1)
 * @param name nome do país
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbProductionCountryResponse(@JsonProperty("iso_3166_1") String iso31661, String name) {
}
