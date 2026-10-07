package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloco {@code data} do envelope GraphQL da query de search.
 *
 * @param page bloco {@code Page} — maiúsculo na AniList, por isso o rename
 */
public record AnilistSearchData(@JsonProperty("Page") AnilistPageResponse page) {
}
