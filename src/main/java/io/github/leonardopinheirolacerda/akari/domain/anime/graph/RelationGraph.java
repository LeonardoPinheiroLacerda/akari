package io.github.leonardopinheirolacerda.akari.domain.anime.graph;

import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.RelationClosure;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Percursos sobre o grafo de relações entre animes ({@link AnimeRelation}) — fechamento por
 * BFS e cadeia de {@code SEQUEL}. Sem estado: lê as arestas pelas consultas da própria
 * entidade, dentro da transação de quem chama. A franquia de cada anime é do
 * {@link FranchiseResolver}.
 */
public final class RelationGraph {

    private RelationGraph() {
    }

    /**
     * BFS bidirecional a partir de {@code startId}, até {@code maxDepth} saltos (use
     * {@code Integer.MAX_VALUE} pra fechar sem limite) — base do grafo de relações.
     *
     * @param startId anime que ancora a busca
     * @param maxDepth profundidade máxima da BFS
     * @return os {@code anilistId} alcançados, incluindo o próprio {@code startId}, e as arestas
     *         percorridas
     */
    public static RelationClosure closure(Integer startId, Integer maxDepth) {
        final Set<Integer> visited = new LinkedHashSet<>();
        visited.add(startId);

        // Uma aresta entre dois níveis volta na consulta dos dois — o mapa por id deduplica.
        final Map<Integer, AnimeRelation> edgesById = new LinkedHashMap<>();

        List<Integer> frontier = List.of(startId);

        for (int level = 0; !frontier.isEmpty() && level < maxDepth; level++) {
            final List<Integer> nextFrontier = new ArrayList<>();

            for (AnimeRelation edge : AnimeRelation.findByAnilistIdsEitherSide(frontier)) {
                edgesById.put(edge.id, edge);

                // Uma das pontas já está visitada (é da fronteira); só a outra entra no próximo nível.
                for (Integer endpointId : List.of(edge.fromAnilistId, edge.toAnilistId)) {
                    if (visited.add(endpointId)) {
                        nextFrontier.add(endpointId);
                    }
                }
            }

            frontier = nextFrontier;
        }

        return new RelationClosure(visited, new ArrayList<>(edgesById.values()));
    }

    /**
     * Segue a cadeia de arestas {@code SEQUEL} a partir da raiz, uma de cada vez; em caso de
     * ramificação (mais de uma aresta {@code SEQUEL} saindo do mesmo anime) desempata pelo
     * menor {@code anilistId}, pra ordem determinística.
     *
     * @param rootId raiz da franquia, primeiro elemento da cadeia
     * @param edges arestas consideradas na cadeia
     * @return a cadeia, na ordem de sequência, com a raiz como primeiro elemento
     */
    public static List<Integer> sequelChainFrom(Integer rootId, List<AnimeRelation> edges) {
        final List<Integer> chain = new ArrayList<>();
        final Set<Integer> visited = new HashSet<>();

        Integer current = rootId;

        while (current != null && visited.add(current)) {
            chain.add(current);
            current = nextSequelId(current, edges);
        }

        return chain;
    }

    private static Integer nextSequelId(Integer fromId, List<AnimeRelation> edges) {
        return edges.stream()
                .filter(edge -> edge.relationType == RelationType.SEQUEL && edge.fromAnilistId.equals(fromId))
                .map(edge -> edge.toAnilistId)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

}
