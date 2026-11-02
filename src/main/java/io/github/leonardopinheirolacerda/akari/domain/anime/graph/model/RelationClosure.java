package io.github.leonardopinheirolacerda.akari.domain.anime.graph.model;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import java.util.List;
import java.util.Set;

/**
 * Resultado da BFS bidirecional do grafo de relações, calculada por
 * {@code RelationGraph#closure} — base do grafo de relações e da franquia.
 *
 * @param animeIds {@code anilistId} alcançados, na ordem de descoberta, incluindo o de partida
 * @param edges arestas percorridas que passaram o filtro de tipo, sem repetição
 */
public record RelationClosure(
        Set<Integer> animeIds,
        List<AnimeRelation> edges
) {
}
