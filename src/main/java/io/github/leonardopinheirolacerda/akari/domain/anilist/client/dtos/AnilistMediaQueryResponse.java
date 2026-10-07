package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

/**
 * Envelope da resposta GraphQL da query de fetch por id da AniList.
 *
 * @param data bloco {@code data} da resposta
 */
public record AnilistMediaQueryResponse(AnilistMediaQueryData data) {
}
