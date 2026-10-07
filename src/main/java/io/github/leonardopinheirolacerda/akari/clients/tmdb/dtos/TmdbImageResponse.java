package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Item de uma lista de imagens (posters, backdrops ou logos) do TMDB.
 *
 * @param aspectRatio proporção largura/altura
 * @param height altura em pixels
 * @param width largura em pixels
 * @param filePath path relativo (ex.: {@code /abc123.jpg})
 * @param iso6391 idioma em ISO 639-1, ou {@code null} para versão neutra
 * @param iso31661 país em ISO 3166-1 (raramente presente em imagens)
 * @param voteAverage nota média dada por usuários
 * @param voteCount total de votos
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbImageResponse(
        @JsonProperty("aspect_ratio") Double aspectRatio,
        Integer height,
        Integer width,
        @JsonProperty("file_path") String filePath,
        @JsonProperty("iso_639_1") String iso6391,
        @JsonProperty("iso_3166_1") String iso31661,
        @JsonProperty("vote_average") Double voteAverage,
        @JsonProperty("vote_count") Integer voteCount
) {
}
