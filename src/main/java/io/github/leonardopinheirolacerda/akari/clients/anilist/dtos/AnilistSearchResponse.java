package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

/**
 * Envelope da resposta GraphQL da query de search da AniList.
 *
 * @param data bloco {@code data} da resposta
 */
public record AnilistSearchResponse(AnilistSearchData data) {
}
