package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import java.util.List;

/**
 * Bloco {@code relations} de um {@code Media} — só presente na query de fetch por id, a de
 * search omite pra manter o payload enxuto.
 *
 * @param edges relações do anime consultado com outros títulos
 */
public record AnilistRelationsResponse(List<AnilistRelationEdgeResponse> edges) {
}
