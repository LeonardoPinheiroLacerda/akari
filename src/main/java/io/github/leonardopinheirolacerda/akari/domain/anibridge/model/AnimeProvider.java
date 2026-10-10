package io.github.leonardopinheirolacerda.akari.domain.anibridge.model;

/**
 * Fonte do id de anime usado pra indexar um {@link MappingSegment}. O anibridge grava sempre
 * {@code ANILIST} — a coluna existe porque o dataset de origem carrega outras fontes
 * ({@code MAL}), útil pra debug.
 */
public enum AnimeProvider {

    ANILIST,
    MAL

}
