package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Uma tradução dentro de {@link TmdbTranslationsResponse} para um par idioma+país.
 *
 * @param iso31661 código do país (ISO 3166-1)
 * @param iso6391 código do idioma (ISO 639-1)
 * @param name nome nativo do idioma
 * @param englishName nome do idioma em inglês
 * @param data conteúdo traduzido
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTranslationEntryResponse(
        @JsonProperty("iso_3166_1") String iso31661,
        @JsonProperty("iso_639_1") String iso6391,
        String name,
        @JsonProperty("english_name") String englishName,
        TmdbTranslationDataResponse data
) {
}
