package io.github.leonardopinheirolacerda.akari.domain.anime.graph;

import io.github.leonardopinheirolacerda.akari.domain.anime.graph.model.FranchiseSections;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.AnimeRelation;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.RelationType;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Season;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Divide os membros de uma franquia em temporadas, filmes e especiais — a mesma classificação
 * pra {@code GET /v1/metadata/animes/{anilistId}/franchise} e pra prévia do detalhe
 * administrativo da franquia.
 */
public final class FranchiseClassifier {

    /**
     * Ordena filmes e especiais por {@code seasonYear}, depois pela ordem natural de
     * {@link Season}, com {@code anilistId} como desempate final — sem uma data de estreia mais
     * precisa persistida localmente.
     */
    private static final Comparator<Anime> MOVIES_AND_SPECIALS_ORDER = Comparator
            .comparing((Anime anime) -> anime.seasonYear, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(FranchiseClassifier::seasonOf, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(anime -> anime.anilistId);

    private FranchiseClassifier() {
    }

    /**
     * Classifica os membros: {@code seasons} é a raiz mais a cadeia de {@code SEQUEL} a partir
     * dela só entre os membros (a cadeia atravessa membros de outro formato, que só não entram),
     * filtrada por {@link RelationType#SERIES_FORMATS}; o resto vai pra filmes
     * ({@link RelationType#MOVIE_FORMATS}) ou especiais (qualquer outro formato), ordenados por
     * ano/estação/id.
     *
     * @param rootId raiz da franquia
     * @param memberById membros da franquia, incluindo a raiz
     * @param edges arestas que tocam os membros; as que não ligam dois membros são ignoradas
     * @return os membros divididos em temporadas, filmes e especiais
     */
    public static FranchiseSections classify(Integer rootId,
                                             Map<Integer, Anime> memberById,
                                             List<AnimeRelation> edges) {

        final List<AnimeRelation> memberSequelEdges = edges.stream()
                .filter(edge -> edge.relationType == RelationType.SEQUEL)
                .filter(edge -> memberById.containsKey(edge.fromAnilistId))
                .filter(edge -> memberById.containsKey(edge.toAnilistId))
                .toList();

        final List<Integer> seasonIds = RelationGraph.sequelChainFrom(rootId, memberSequelEdges)
                .stream()
                .filter(id -> RelationType.SERIES_FORMATS.contains(memberById.get(id).format))
                .toList();

        final Set<Integer> seasonIdSet = Set.copyOf(seasonIds);

        final Map<Boolean, List<Integer>> othersByIsMovie = memberById.values()
                .stream()
                .filter(anime -> !seasonIdSet.contains(anime.anilistId))
                .sorted(MOVIES_AND_SPECIALS_ORDER)
                .collect(Collectors.partitioningBy(
                        anime -> RelationType.MOVIE_FORMATS.contains(anime.format),
                        Collectors.mapping(anime -> anime.anilistId, Collectors.toList())
                ));

        return new FranchiseSections(
                seasonIds,
                othersByIsMovie.get(true),
                othersByIsMovie.get(false)
        );
    }

    /**
     * Converte {@code Anime#season} (string crua da AniList) pro {@link Season}, pra ordenar
     * {@link #MOVIES_AND_SPECIALS_ORDER} pela ordem natural do enum em vez de comparar strings.
     *
     * @param anime anime cujo {@code season} se converte
     * @return o {@link Season} correspondente; {@code null} se for {@code null} ou não bater
     *         com nenhuma constante conhecida
     */
    private static Season seasonOf(Anime anime) {
        if (anime.season == null) {
            return null;
        }
        try {
            return Season.valueOf(anime.season);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

}
