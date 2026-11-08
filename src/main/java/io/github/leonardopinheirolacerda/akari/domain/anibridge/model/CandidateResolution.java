package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

/**
 * Resultado da escolha do candidato TMDB pra um anime (ver {@code AnibridgeResolutionService}):
 * o melhor candidato e o que se sabe sobre a confiabilidade da escolha, calculados numa busca só.
 *
 * @param best melhor candidato; {@code null} se o anibridge não tem nada pra esse anime
 * @param candidateCount quantos candidatos ({@code tmdbId}/{@code mediaType} distintos) existem
 * @param bestSuspicious {@code true} se o melhor candidato é suspeito (sobreposição interna ou
 *                       cobertura maior que o anime)
 * @param tied {@code true} se o melhor empata com o segundo em todos os critérios da escolha
 */
public record CandidateResolution(
        TmdbMappingCandidate best,
        int candidateCount,
        boolean bestSuspicious,
        boolean tied
) {
}
