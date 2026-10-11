package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;

/**
 * Resultado da conversão de um episódio do anime pro TMDB — temporada e episódio já resolvidos
 * a partir do {@link MappingSegment} que cobre o episódio de origem.
 *
 * @param seasonNumber temporada no TMDB, ou {@code null} pra filme
 * @param tmdbEpisode episódio no TMDB
 */
public record ResolvedEpisode(Integer seasonNumber, Integer tmdbEpisode) {
}
