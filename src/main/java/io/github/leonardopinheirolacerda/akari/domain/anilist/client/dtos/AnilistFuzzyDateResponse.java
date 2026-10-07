package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

/**
 * Data parcial da AniList — qualquer componente pode vir {@code null}.
 *
 * @param year ano, ou {@code null} se desconhecido
 * @param month mês, ou {@code null} se desconhecido
 * @param day dia, ou {@code null} se desconhecido
 */
public record AnilistFuzzyDateResponse(Integer year, Integer month, Integer day) {
}
