package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

/**
 * URLs de poster da AniList em 3 resoluções — cada campo pode vir {@code null}.
 *
 * @param medium URL em tamanho pequeno (~100×145)
 * @param large URL em tamanho médio (~230×340)
 * @param extraLarge URL em tamanho grande (~460×645)
 */
public record AnilistCoverImageResponse(String medium, String large, String extraLarge) {
}
