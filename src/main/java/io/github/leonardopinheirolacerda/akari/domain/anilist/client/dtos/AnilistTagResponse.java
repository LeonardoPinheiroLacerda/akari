package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

/**
 * Tag de um {@code Media} da AniList.
 *
 * @param name nome da tag
 * @param rank quanto a tag se aplica ao anime, de 0 a 100
 * @param isMediaSpoiler {@code true} se a tag revela parte da história
 */
public record AnilistTagResponse(String name, Integer rank, Boolean isMediaSpoiler) {
}
