package io.github.leonardopinheirolacerda.akari.domain.anibridge.parser.model;

/**
 * Um range de episódios extraído do dataset do anibridge, como {@code "1-26"} ou {@code "1089-"}
 * (aberto).
 *
 * @param start episódio inicial (1-based, inclusive)
 * @param end episódio final (inclusive), ou {@code null} se o range for aberto
 */
public record EpisodeRange(Integer start, Integer end) {
}
