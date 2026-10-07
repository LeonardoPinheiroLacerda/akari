package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

import java.util.List;

/**
 * Bloco {@code Page} da resposta GraphQL da query de search.
 *
 * @param pageInfo metadados de paginação
 * @param media itens encontrados nesta página
 */
public record AnilistPageResponse(AnilistPageInfoResponse pageInfo, List<AnilistMediaResponse> media) {
}
