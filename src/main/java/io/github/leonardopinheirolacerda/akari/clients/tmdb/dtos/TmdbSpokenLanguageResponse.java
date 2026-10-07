package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Idioma falado numa obra no TMDB.
 *
 * @param englishName nome do idioma em inglês
 * @param iso6391 código do idioma (ISO 639-1)
 * @param name nome nativo do idioma
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbSpokenLanguageResponse(
        @JsonProperty("english_name") String englishName,
        @JsonProperty("iso_639_1") String iso6391,
        String name
) {
}
