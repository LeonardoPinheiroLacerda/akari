package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

/**
 * Cobertura de episódios de um candidato TMDB, já descontando sobreposição de range entre
 * segments (ver {@code AnibridgeResolutionService}).
 *
 * @param episodeCount total de episódios distintos cobertos, com sobreposição mesclada (não
 *                      contada em dobro)
 * @param hasOverlap {@code true} se havia sobreposição entre os segments originais
 */
public record CoverageResult(int episodeCount, boolean hasOverlap) {
}
