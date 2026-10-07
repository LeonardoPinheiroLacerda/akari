package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Membro da equipe (diretor, roteirista, etc.) de um episódio no TMDB.
 *
 * @param department departamento ({@code "Directing"}, {@code "Writing"}, ...)
 * @param job cargo específico ({@code "Director"}, {@code "Screenplay"}, ...)
 * @param creditId credit id específico para essa participação
 * @param adult flag de conteúdo adulto associado à pessoa
 * @param gender código de gênero do TMDB
 * @param tmdbId id da pessoa no TMDB
 * @param knownForDepartment departamento pelo qual a pessoa é mais conhecida
 * @param name nome da pessoa
 * @param originalName nome original
 * @param popularity métrica de popularidade do TMDB para a pessoa
 * @param profilePath path relativo da foto de perfil
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbCrewMemberResponse(
        String department,
        String job,
        @JsonProperty("credit_id") String creditId,
        Boolean adult,
        Integer gender,
        @JsonProperty("id") Integer tmdbId,
        @JsonProperty("known_for_department") String knownForDepartment,
        String name,
        @JsonProperty("original_name") String originalName,
        Double popularity,
        @JsonProperty("profile_path") String profilePath
) {
}
