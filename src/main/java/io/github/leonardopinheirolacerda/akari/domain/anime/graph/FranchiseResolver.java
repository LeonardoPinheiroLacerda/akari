package io.github.leonardopinheirolacerda.akari.domain.anime.graph;

import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseAssignment;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FranchiseNode;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resolve a franquia de todos os animes da coleção de uma vez, em memória — sem coluna
 * materializada, então nunca fica desatualizada depois de um sync de relações, bind ou unbind.
 * Lê só o necessário (atribuição de cada anime e as arestas que geram pai automático),
 * dentro da transação de quem chama.
 *
 * <p><b>Pai automático</b> (no máximo um por anime), em ordem de prioridade: {@code PREQUEL}
 * saindo dele, {@code PARENT} saindo dele, {@link RelationType#DERIVED_LINKS} chegando nele.
 * Dentro da mesma prioridade, desempata pelo ano mais antigo (sem ano por último), depois pelo
 * menor {@code anilistId}.
 *
 * <p><b>Franquia de um anime</b> ({@code franchiseOf}), pela atribuição: {@code HIDDEN} →
 * nenhuma; {@code MANUAL_ROOT} → ela mesma; {@code MANUAL_MEMBER} → a franquia da raiz escolhida
 * (que, enquanto for raiz, é ela mesma); {@code AUTO} sem pai → ela mesma; {@code AUTO} com pai
 * → a franquia do pai.
 * Ciclo na subida → o anime onde o ciclo foi detectado vira raiz.
 *
 * <p>Só {@link #resolve} e {@link #resolveAgain} são públicos; a instância é interna, só pra
 * guardar o estado da resolução em andamento.
 */
public final class FranchiseResolver {

    private static final int PREQUEL_PRIORITY = 1;

    private static final int PARENT_PRIORITY = 2;

    private static final int DERIVED_PRIORITY = 3;

    private final Map<Integer, FranchiseNode> nodeById;

    private final Map<Integer, Integer> autoParentById;

    private final Map<Integer, Integer> rootById = new HashMap<>();

    private final Set<Integer> outsideIds = new HashSet<>();

    private FranchiseResolver(Map<Integer, FranchiseNode> nodeById,
                              Map<Integer, Integer> autoParentById) {

        this.nodeById = nodeById;
        this.autoParentById = autoParentById;
    }

    /**
     * Resolve a franquia de todos os animes da coleção.
     *
     * @return a franquia de cada anime, o pai automático de cada um e as raízes manuais
     */
    public static FranchiseResolution resolve() {
        final Map<Integer, FranchiseNode> nodeById = findNodes();
        final Map<Integer, Integer> autoParentById = autoParents(
                nodeById,
                AnimeRelation.findByTypes(RelationType.AUTO_PARENT_LINKS)
        );

        return resolve(nodeById, autoParentById);
    }

    /**
     * Resolve de novo depois de mudar só atribuições (organização no portal), reaproveitando o
     * pai automático de {@code before} — essas mudanças não mexem nas arestas nem no ano dos
     * animes, então só a atribuição de cada anime é relida.
     *
     * @param before resolução lida antes da mudança, na mesma transação
     * @return a franquia de cada anime, já com a mudança
     */
    public static FranchiseResolution resolveAgain(FranchiseResolution before) {
        return resolve(findNodes(), before.autoParentById());
    }

    private static Map<Integer, FranchiseNode> findNodes() {
        return Anime.findFranchiseNodes()
                .stream()
                .collect(Collectors.toMap(FranchiseNode::anilistId, Function.identity()));
    }

    private static FranchiseResolution resolve(Map<Integer, FranchiseNode> nodeById,
                                               Map<Integer, Integer> autoParentById) {

        final Set<Integer> manualRootIds = nodeById.values()
                .stream()
                .filter(node -> node.assignment() == FranchiseAssignment.MANUAL_ROOT)
                .map(FranchiseNode::anilistId)
                .collect(Collectors.toSet());
        final FranchiseResolver resolver = new FranchiseResolver(nodeById, autoParentById);

        for (Integer anilistId : nodeById.keySet()) {
            resolver.resolveFrom(anilistId);
        }

        return new FranchiseResolution(
                resolver.rootById,
                autoParentById,
                manualRootIds,
                resolver.outsideIds
        );
    }

    /**
     * Resolve a franquia de {@code startId} e de todos os animes ainda não resolvidos no
     * caminho da subida — todos ficam na mesma franquia.
     *
     * @param startId anime de onde a subida começa
     */
    private void resolveFrom(Integer startId) {
        final Set<Integer> path = new LinkedHashSet<>();
        final Integer rootId = climb(startId, path);

        if (rootId == null) {
            outsideIds.addAll(path);
        } else {
            path.forEach(pathId -> rootById.put(pathId, rootId));
        }
    }

    /**
     * Sobe a partir de {@code startId} até achar a franquia, anotando em {@code path} os animes
     * percorridos que ainda não estavam resolvidos.
     *
     * @param startId anime de onde a subida começa
     * @param path recebe os animes percorridos, na ordem
     * @return a raiz da franquia, ou {@code null} se fica fora de qualquer franquia
     */
    private Integer climb(Integer startId, Set<Integer> path) {
        Integer current = startId;

        while (true) {
            if (rootById.containsKey(current)) {
                return rootById.get(current);
            }
            if (outsideIds.contains(current)) {
                return null;
            }
            if (!path.add(current)) {
                return current;
            }

            final FranchiseNode node = nodeById.get(current);

            if (node.assignment() == FranchiseAssignment.HIDDEN) {
                return null;
            }
            if (node.assignment() == FranchiseAssignment.MANUAL_ROOT) {
                return current;
            }
            if (node.assignment() == FranchiseAssignment.MANUAL_MEMBER && node.franchiseRootId() != null) {
                current = node.franchiseRootId();
                continue;
            }
            if (!autoParentById.containsKey(current)) {
                return current;
            }

            current = autoParentById.get(current);
        }
    }

    /**
     * Pai automático de cada anime que tem um — ver a prioridade no javadoc da classe.
     *
     * @param nodeById animes da coleção, pro desempate por ano
     * @param edges arestas de {@link RelationType#AUTO_PARENT_LINKS}
     * @return o pai automático de cada anime que tem candidato
     */
    private static Map<Integer, Integer> autoParents(Map<Integer, FranchiseNode> nodeById,
                                                     List<AnimeRelation> edges) {

        final Map<Integer, TreeMap<Integer, List<Integer>>> candidatesByPriority = new HashMap<>();

        for (AnimeRelation edge : edges) {
            if (edge.fromAnilistId.equals(edge.toAnilistId)) {
                continue;
            }

            if (edge.relationType == RelationType.PREQUEL) {
                addCandidate(candidatesByPriority, edge.fromAnilistId, PREQUEL_PRIORITY, edge.toAnilistId);
            } else if (edge.relationType == RelationType.PARENT) {
                addCandidate(candidatesByPriority, edge.fromAnilistId, PARENT_PRIORITY, edge.toAnilistId);
            } else if (RelationType.DERIVED_LINKS.contains(edge.relationType)) {
                addCandidate(candidatesByPriority, edge.toAnilistId, DERIVED_PRIORITY, edge.fromAnilistId);
            }
        }

        final Comparator<Integer> oldestFirst = Comparator
                .comparing(
                        (Integer id) -> nodeById.get(id).seasonYear(),
                        Comparator.nullsLast(Comparator.naturalOrder())
                )
                .thenComparing(Comparator.naturalOrder());

        final Map<Integer, Integer> autoParentById = new HashMap<>();

        candidatesByPriority.forEach((anilistId, candidates) -> autoParentById.put(
                anilistId,
                candidates.firstEntry()
                        .getValue()
                        .stream()
                        .min(oldestFirst)
                        .orElseThrow()
        ));

        return autoParentById;
    }

    private static void addCandidate(Map<Integer, TreeMap<Integer, List<Integer>>> candidatesByPriority,
                                     Integer anilistId,
                                     Integer priority,
                                     Integer candidateId) {

        candidatesByPriority
                .computeIfAbsent(anilistId, id -> new TreeMap<>())
                .computeIfAbsent(priority, p -> new ArrayList<>())
                .add(candidateId);
    }

}
