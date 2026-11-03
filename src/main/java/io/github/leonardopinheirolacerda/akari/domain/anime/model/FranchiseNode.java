package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseAssignment;

/**
 * Dados de franquia de um anime, projetados por {@code Anime#findFranchiseNodes} — só o que a
 * resolução de franquias precisa, sem carregar a entidade inteira.
 *
 * @param anilistId id do anime
 * @param seasonYear ano de estreia, usado no desempate do pai automático
 * @param assignment atribuição de franquia
 * @param franchiseRootId raiz escolhida à mão, com atribuição {@code MANUAL_MEMBER}
 */
public record FranchiseNode(
        Integer anilistId,
        Integer seasonYear,
        FranchiseAssignment assignment,
        Integer franchiseRootId
) {
}
