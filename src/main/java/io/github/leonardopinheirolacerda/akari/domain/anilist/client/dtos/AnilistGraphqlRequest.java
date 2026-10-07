package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import io.github.leonardopinheirolacerda.akari.domain.anilist.client.AnilistQueries;
import java.util.Map;

/**
 * Corpo de uma requisição GraphQL à AniList.
 *
 * @param query corpo da query GraphQL (ver {@link AnilistQueries})
 * @param variables variáveis da query, por nome
 */
public record AnilistGraphqlRequest(String query, Map<String, Object> variables) {
}
