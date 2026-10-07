package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

/**
 * Metadados de paginação devolvidos pela AniList — paginação 1-based.
 *
 * @param total total de itens que casam com a busca
 * @param perPage tamanho de página usado na consulta
 * @param currentPage página atual (1-based)
 * @param lastPage última página disponível
 * @param hasNextPage {@code true} se há próxima página
 */
public record AnilistPageInfoResponse(
        Integer total,
        Integer perPage,
        Integer currentPage,
        Integer lastPage,
        Boolean hasNextPage
) {
}
