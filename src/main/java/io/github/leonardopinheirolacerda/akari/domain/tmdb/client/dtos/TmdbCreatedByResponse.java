package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Criador/showrunner de uma série no TMDB.
 *
 * @param tmdbId id da pessoa no TMDB
 * @param creditId credit id específico para essa participação
 * @param name nome da pessoa
 * @param originalName nome original
 * @param gender código de gênero do TMDB (0=não especificado, 1=feminino, 2=masculino, 3=não-binário)
 * @param profilePath path relativo da foto de perfil
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCreatedByResponse(
        @JsonProperty("id") Integer tmdbId,
        @JsonProperty("credit_id") String creditId,
        String name,
        @JsonProperty("original_name") String originalName,
        Integer gender,
        @JsonProperty("profile_path") String profilePath
) {
}
