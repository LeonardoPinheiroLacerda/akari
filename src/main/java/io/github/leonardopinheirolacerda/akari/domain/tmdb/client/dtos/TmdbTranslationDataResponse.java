package io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Payload traduzido dentro de {@link TmdbTranslationEntryResponse}.
 *
 * <p>{@code localizedName} aceita {@code title} (movie) ou {@code name} (tv) — só um vem
 * preenchido, dependendo do endpoint de origem; por isso os dois nomes como alias, em vez de
 * um {@code @JsonProperty} único.
 *
 * @param localizedName título (movie) ou nome (tv) traduzido
 * @param overview sinopse traduzida
 * @param homepage homepage localizada
 * @param tagline tagline traduzida
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbTranslationDataResponse(
        @JsonAlias({"title", "name"}) String localizedName,
        String overview,
        String homepage,
        String tagline
) {
}
