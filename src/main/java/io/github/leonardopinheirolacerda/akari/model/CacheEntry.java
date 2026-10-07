package io.github.leonardopinheirolacerda.akari.model;

import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
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
