package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope compartilhado de {@code /3/movie/{id}/translations} e
 * {@code /3/tv/{id}/translations}.
 *
 * @param translations lista de traduções por par idioma+país
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTranslationsResponse(List<TmdbTranslationEntryResponse> translations) {
}
