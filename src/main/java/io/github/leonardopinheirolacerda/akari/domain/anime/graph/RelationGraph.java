package io.github.leonardopinheirolacerda.akari.domain.anime.graph;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRootCuration;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.RelationClosure;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import io.quarkus.logging.Log;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Percursos sobre o grafo de relações entre animes ({@link AnimeRelation}) — fechamento por
 * BFS, subida até a raiz da franquia, cadeia de {@code SEQUEL} e classificação de raiz. Sem
 * estado: lê as arestas e os animes pelas consultas das próprias entidades, dentro da transação
 * de quem chama.
 */
public final class RelationGraph {

    private RelationGraph() {
    }

    /**
     * BFS bidirecional a partir de {@code startId}, até {@code maxDepth} saltos (use
     * {@code Integer.MAX_VALUE} pra fechar sem limite), só pelas arestas que casam
     * {@code edgeFilter} — base do grafo de relações (sem filtro de tipo, com {@code depth}) e da
     * franquia (filtrado por {@link RelationType#FRANCHISE_LINKS}, sem limite).
     *
     * @param startId anime que ancora a busca
     * @param maxDepth profundidade máxima da BFS
     * @param edgeFilter tipos de relação que contam como aresta do fechamento
     * @return os {@code anilistId} alcançados, incluindo o próprio {@code startId}, e as arestas
     *         percorridas que passaram o filtro
     */
    public static RelationClosure closure(Integer startId,
                                          Integer maxDepth,
                                          Predicate<RelationType> edgeFilter) {

        final Set<Integer> visited = new LinkedHashSet<>();
        visited.add(startId);

        // Uma aresta entre dois níveis volta na consulta dos dois — o mapa por id deduplica.
        final Map<Integer, AnimeRelation> edgesById = new LinkedHashMap<>();

        List<Integer> frontier = List.of(startId);

        for (int level = 0; !frontier.isEmpty() && level < maxDepth; level++) {
            final List<Integer> nextFrontier = new ArrayList<>();

            for (AnimeRelation edge : AnimeRelation.findByAnilistIdsEitherSide(frontier)) {
                if (!edgeFilter.test(edge.relationType)) {
                    continue;
                }

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
     * Sobe a cadeia de ancestrais a partir do anime informado até achar a raiz da franquia — a
     * mesma regra de {@code Anime#findRoots}: para numa curadoria {@code MANUAL_ROOT} mesmo que
     * ela ainda tenha uma aresta de ancestral, ou quando não sobra mais nenhuma pra subir. Em
     * caso de mais de uma aresta de ancestral saindo do mesmo anime, desempata pelo menor
     * {@code anilistId}, pra subida determinística — mesmo critério de {@link #sequelChainFrom}.
     *
     * <p>Se a subida acabar formando um ciclo (ex.: dois animes se referenciando como
     * ancestrais um do outro), devolve o último pivot processado com sucesso antes do ciclo,
     * não o nó que causou a repetição — evita que a ordem de descoberta decida a raiz
     * arbitrariamente.
     *
     * @param anilistId anime de onde a subida começa; precisa existir
     * @return o {@code anilistId} da raiz
     * @see #isFranchiseAncestorEdge
     */
    public static Integer findFranchiseRootId(Integer anilistId) {
        final Set<Integer> visited = new HashSet<>();

        Integer current = anilistId;
        Integer lastPivot = anilistId;

        while (visited.add(current)) {
            final Integer pivot = current;
            lastPivot = pivot;
            final Anime anime = Anime.find(pivot).orElseThrow();

            if (anime.franchiseRootCuration == FranchiseRootCuration.MANUAL_ROOT) {
                return pivot;
            }

            final Optional<Integer> ancestorId = AnimeRelation.findByAnilistIdEitherSide(pivot)
                    .stream()
                    .filter(edge -> isFranchiseAncestorEdge(edge, pivot))
                    .map(edge -> otherSideOf(edge, pivot))
                    .min(Comparator.naturalOrder());

            if (ancestorId.isEmpty()) {
                if (anime.franchiseRootCuration == FranchiseRootCuration.MANUAL_NOT_ROOT) {
                    Log.warnf(
                            "Anime %d está curado como MANUAL_NOT_ROOT mas não tem aresta de "
                                    + "ancestral pra subir; usando como raiz da franquia por falta de "
                                    + "alternativa",
                            pivot);
                }
                return pivot;
            }

            current = ancestorId.get();
        }

        return lastPivot;
    }

    /**
     * Segue a cadeia de arestas {@code SEQUEL} a partir da raiz, uma de cada vez; em caso de
     * ramificação (mais de uma aresta {@code SEQUEL} saindo do mesmo anime) desempata pelo
     * menor {@code anilistId}, pra ordem determinística.
     *
     * @param rootId raiz da franquia, primeiro elemento da cadeia
     * @param edges arestas já coletadas por {@link #closure}
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

    /**
     * {@code true} se o anime tiver, em qualquer ponta, uma aresta de {@link
     * RelationType#SPECIAL_LINKS} — usado pra não excluir da franquia um anime da cadeia de
     * {@code SEQUEL} com formato desconhecido quando ele também está ligado como especial por
     * outro caminho (ex.: {@code SIDE_STORY}).
     *
     * @param id anime a verificar
     * @param edges arestas já coletadas por {@link #closure}
     * @return {@code true} se houver pelo menos uma aresta de {@code SPECIAL_LINKS} tocando o id
     */
    public static boolean hasDirectSpecialLink(Integer id, List<AnimeRelation> edges) {
        return edges.stream()
                .anyMatch(edge ->
                        (edge.fromAnilistId.equals(id) || edge.toAnilistId.equals(id))
                                && RelationType.SPECIAL_LINKS.contains(edge.relationType));
    }

    /**
     * Classifica o anime como raiz manual, raiz natural ou não-raiz: a curadoria manual
     * prevalece; em {@code AUTO}, é não-raiz se sair dele uma aresta de
     * {@link RelationType#ANCESTOR_LINKS}.
     *
     * @param anime anime a classificar
     * @param edges arestas já coletadas por {@link #closure}
     * @return o tipo de raiz do anime
     */
    public static AnimeRootKind rootKindOf(Anime anime, List<AnimeRelation> edges) {
        return switch (anime.franchiseRootCuration) {
            case MANUAL_ROOT -> AnimeRootKind.MANUAL;
            case MANUAL_NOT_ROOT -> AnimeRootKind.NOT_ROOT;
            case AUTO -> hasAncestorLink(anime.anilistId, edges) ? AnimeRootKind.NOT_ROOT : AnimeRootKind.NATURAL;
        };
    }

    /**
     * {@code true} se a aresta indicar que {@code pivot} é derivado/descende do outro lado,
     * pra subida de {@link #findFranchiseRootId}. Saindo de {@code pivot}, vale pra qualquer
     * tipo de {@link RelationType#ANCESTOR_LINKS}; chegando em {@code pivot}, só pra
     * {@code SUMMARY} — essa é a única direção invertida (ver javadoc da constante).
     * {@code ALTERNATIVE} não entra em nenhuma direção: a AniList tagueia esse tipo de forma
     * simétrica com frequência, o que criaria um ciclo sem um lado objetivamente ancestral.
     *
     * @param edge aresta a avaliar
     * @param pivot anime cuja ancestralidade se verifica
     * @return {@code true} se a aresta aponta pra um ancestral de {@code pivot}
     */
    private static boolean isFranchiseAncestorEdge(AnimeRelation edge, Integer pivot) {
        if (edge.fromAnilistId.equals(pivot)) {
            return RelationType.ANCESTOR_LINKS.contains(edge.relationType);
        }
        return edge.relationType == RelationType.SUMMARY;
    }

    private static Integer otherSideOf(AnimeRelation edge, Integer pivot) {
        if (edge.fromAnilistId.equals(pivot)) {
            return edge.toAnilistId;
        }
        return edge.fromAnilistId;
    }

    private static Integer nextSequelId(Integer fromId, List<AnimeRelation> edges) {
        return edges.stream()
                .filter(edge -> edge.relationType == RelationType.SEQUEL && edge.fromAnilistId.equals(fromId))
                .map(edge -> edge.toAnilistId)
                .min(Comparator.naturalOrder())
                .orElse(null);
    }

    private static boolean hasAncestorLink(Integer anilistId, List<AnimeRelation> edges) {
        return edges.stream()
                .anyMatch(edge ->
                        edge.fromAnilistId.equals(anilistId)
                                && RelationType.ANCESTOR_LINKS.contains(edge.relationType));
    }

}
