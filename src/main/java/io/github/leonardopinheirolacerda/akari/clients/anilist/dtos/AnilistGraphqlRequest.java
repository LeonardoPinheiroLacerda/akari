package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

import java.util.Map;

/**
 * Corpo de uma requisição GraphQL à AniList.
 *
 * @param query corpo da query GraphQL (ver {@link io.github.leonardopinheirolacerda.akari.clients.anilist.AnilistQueries})
 * @param variables variáveis da query, por nome
 */
public record AnilistGraphqlRequest(String query, Map<String, Object> variables) {
}
