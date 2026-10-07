package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Gênero associado a uma obra no TMDB.
 *
 * @param tmdbId identificador do gênero no TMDB
 * @param name nome do gênero em inglês (ou no idioma da query)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbGenreResponse(@JsonProperty("id") Integer tmdbId, String name) {
}
