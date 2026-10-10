package io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.AnimeProvider;

/**
 * Descritor de origem decodificado do dataset do anibridge — {@code anilist:<id>} ou
 * {@code mal:<id>}.
 *
 * @param provider fonte do id
 * @param animeId id do anime na fonte
 */
public record AnimeSourceDescriptor(AnimeProvider provider, Integer animeId) {
}
