package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloco {@code data} do envelope GraphQL da query de fetch por id.
 *
 * @param media bloco {@code Media} — maiúsculo na AniList, por isso o rename
 */
public record AnilistMediaQueryData(@JsonProperty("Media") AnilistMediaResponse media) {
}
