package io.github.leonardopinheirolacerda.akari.domain.anime.graph.model;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRootKind;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseKind;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Franquia de cada anime da coleção, calculada por {@code FranchiseResolver#resolve} — fonte
 * única de "a que franquia este anime pertence" pra todas as vistas.
 *
 * @param rootById raiz da franquia de cada anime ({@code franchiseOf}); ausente pros que estão
 *                 fora de qualquer franquia
 * @param autoParentById pai automático de cada anime que tem um
 * @param manualRootIds raízes manuais (atribuição {@code MANUAL_ROOT})
 * @param outsideIds animes fora de qualquer franquia (ocultos, direto ou por herança)
 */
public record FranchiseResolution(
        Map<Integer, Integer> rootById,
        Map<Integer, Integer> autoParentById,
        Set<Integer> manualRootIds,
        Set<Integer> outsideIds
) {

    /**
     * Raiz da franquia do anime.
     *
     * @param anilistId anime
     * @return a raiz, ou {@code null} se o anime está fora de qualquer franquia
     */
    public Integer rootOf(Integer anilistId) {
        return rootById.get(anilistId);
    }

    /**
     * Pai automático do anime.
     *
     * @param anilistId anime
     * @return o pai automático, ou {@code null} se não tem
     */
    public Integer autoParentOf(Integer anilistId) {
        return autoParentById.get(anilistId);
    }

    /**
     * {@code true} se o anime é raiz atual — a franquia dele é ele mesmo.
     *
     * @param anilistId anime
     * @return se é raiz
     */
    public boolean isRoot(Integer anilistId) {
        return anilistId.equals(rootById.get(anilistId));
    }

    /**
     * Tipo de uma raiz: manual se foi criada à mão, natural nos demais casos.
     *
     * @param rootId raiz atual
     * @return o tipo da raiz
     */
    public FranchiseKind kindOf(Integer rootId) {
        if (manualRootIds.contains(rootId)) {
            return FranchiseKind.MANUAL;
        }
        return FranchiseKind.NATURAL;
    }

    /**
     * Classificação de raiz do anime pro nó do grafo de relações.
     *
     * @param anilistId anime
     * @return {@code MANUAL}/{@code NATURAL} se é raiz atual, {@code NOT_ROOT} senão
     */
    public AnimeRootKind rootKindOf(Integer anilistId) {
        if (!isRoot(anilistId)) {
            return AnimeRootKind.NOT_ROOT;
        }
        if (manualRootIds.contains(anilistId)) {
            return AnimeRootKind.MANUAL;
        }
        return AnimeRootKind.NATURAL;
    }

    /**
     * Todas as raízes atuais.
     *
     * @return os {@code anilistId} das raízes, em ordem crescente
     */
    public List<Integer> rootIds() {
        return rootById.keySet()
                .stream()
                .filter(this::isRoot)
                .sorted()
                .toList();
    }

    /**
     * Membros da franquia, incluindo a raiz.
     *
     * @param rootId raiz da franquia
     * @return os {@code anilistId} dos membros, em ordem crescente
     */
    public List<Integer> membersOf(Integer rootId) {
        return rootById.entrySet()
                .stream()
                .filter(entry -> entry.getValue().equals(rootId))
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
    }

    /**
     * Quantidade de membros de cada franquia, incluindo a raiz.
     *
     * @return a contagem por raiz
     */
    public Map<Integer, Long> memberCountByRoot() {
        return rootById.values()
                .stream()
                .collect(Collectors.groupingBy(
                        rootId -> rootId,
                        Collectors.counting()
                ));
    }

    /**
     * Animes fora de qualquer franquia.
     *
     * @return os {@code anilistId}, em ordem crescente
     */
    public List<Integer> sortedOutsideIds() {
        return outsideIds.stream()
                .sorted()
                .toList();
    }

}
