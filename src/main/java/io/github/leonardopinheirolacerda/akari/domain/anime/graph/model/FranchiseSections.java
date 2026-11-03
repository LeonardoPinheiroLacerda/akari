package io.github.leonardopinheirolacerda.akari.domain.anime.graph.model;

import java.util.List;

/**
 * Membros de uma franquia divididos em temporadas, filmes e especiais, calculados por
 * {@code FranchiseClassifier#classify}.
 *
 * @param seasonIds a raiz mais a cadeia de {@code SEQUEL} entre os membros, só formatos de série,
 *                  na ordem da cadeia
 * @param movieIds demais membros de formato {@code MOVIE}, por ano/estação/id
 * @param specialIds demais membros de qualquer outro formato, por ano/estação/id
 */
public record FranchiseSections(
        List<Integer> seasonIds,
        List<Integer> movieIds,
        List<Integer> specialIds
) {
}
