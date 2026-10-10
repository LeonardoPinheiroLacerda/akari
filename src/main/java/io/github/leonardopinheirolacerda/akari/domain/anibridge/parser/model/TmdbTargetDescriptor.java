package io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;

/**
 * Descritor de destino TMDB decodificado do dataset do anibridge — {@code tmdb_show:<id>:s<n>}
 * ou {@code tmdb_movie:<id>}.
 *
 * @param tmdbId id do recurso no TMDB
 * @param mediaType tipo do recurso
 * @param seasonNumber número da temporada, ou {@code null} para {@link TmdbMediaType#MOVIE}
 */
public record TmdbTargetDescriptor(Integer tmdbId, TmdbMediaType mediaType, Integer seasonNumber) {
}
