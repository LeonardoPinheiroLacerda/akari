package io.github.leonardopinheirolacerda.akari.utils;

import io.github.leonardopinheirolacerda.akari.model.CacheEntry;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Helpers de cache-first pra buscas em sistemas externos (AniList, TMDB, ...): montagem de chave
 * e o fluxo servir-do-cache-ou-buscar-no-upstream. Agnóstico de onde o cache é persistido — cada
 * chamador decide como ler/escrever sua própria entidade.
 */
public final class CacheUtils {

    private CacheUtils() {
    }

    /**
     * Monta uma chave lógica de cache concatenando as partes informadas, normalizadas
     * (trim + lowercase) e unidas por {@code ":"}. O número e o significado das partes ficam a
     * critério do chamador — serve tanto pra uma busca paginada
     * ({@code buildKey(query, "0", "12")}) quanto pra um lookup por id
     * ({@code buildKey("media", String.valueOf(anilistId))}).
     *
     * @param parts segmentos da chave, na ordem desejada; {@code null} vira {@code ""}
     * @return os segmentos normalizados e unidos por {@code ":"}
     */
    public static String buildKey(String... parts) {
        return Stream.of(parts)
                .map(part -> part == null ? "" : part.trim().toLowerCase())
                .collect(Collectors.joining(":"));
    }

    /**
     * Resolve um valor via cache-first: devolve o que {@code cacheReader} achar se estiver
     * dentro do {@code ttl} (a menos que {@code forceRefresh} seja {@code true}); senão chama
     * {@code loader}, grava o resultado via {@code writer} e devolve o valor recém-buscado.
     *
     * @param forceRefresh {@code true} ignora o cache e força a chamada a {@code loader}
     * @param ttl tempo máximo de vida de uma entrada de cache
     * @param cacheReader busca a entrada de cache atual pela chave já conhecida pelo chamador
     * @param loader chamada ao sistema externo, usada quando o cache está ausente, vencido ou
     *               {@code forceRefresh} é {@code true}
     * @param writer grava o valor recém-buscado no cache do chamador
     * @param <T> tipo do valor cacheado
     * @return o valor do cache quando fresco, ou o resultado de {@code loader}
     */
    public static <T> T resolve(
            Boolean forceRefresh,
            Duration ttl,
            Supplier<Optional<CacheEntry<T>>> cacheReader,
            Supplier<T> loader,
            Consumer<T> writer) {

        if (!Boolean.TRUE.equals(forceRefresh)) {
            final Optional<CacheEntry<T>> cached = cacheReader.get();

            if (cached.isPresent() && isFresh(cached.get().fetchedAt(), ttl)) {
                return cached.get().payload();
            }
        }

        final T fresh = loader.get();

        writer.accept(fresh);

        return fresh;
    }

    private static boolean isFresh(OffsetDateTime fetchedAt, Duration ttl) {
        return fetchedAt != null && Duration.between(fetchedAt, OffsetDateTime.now()).compareTo(ttl) < 0;
    }

}
