package io.github.leonardopinheirolacerda.akari.utils;

import java.time.OffsetDateTime;

/**
 * Par payload/timestamp lido do cache do chamador de {@link CacheUtils#resolve}, agnóstico da
 * entidade concreta.
 *
 * @param payload valor cacheado
 * @param fetchedAt momento em que o valor foi buscado do upstream
 * @param <T> tipo do valor cacheado
 */
public record CacheEntry<T>(T payload, OffsetDateTime fetchedAt) {
}
