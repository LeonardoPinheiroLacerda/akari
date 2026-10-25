package io.github.leonardopinheirolacerda.akari.domain.anime.model;

/**
 * Estação de estreia bruta da AniList ({@code Anime#season}), na ordem natural do ano — usada
 * pra ordenar {@code specials}/{@code movies} de {@code GET
 * /v1/metadata/animes/{anilistId}/franchise} quando não há uma data de estreia mais precisa
 * persistida localmente.
 */
public enum Season {
    WINTER,
    SPRING,
    SUMMER,
    FALL
}
