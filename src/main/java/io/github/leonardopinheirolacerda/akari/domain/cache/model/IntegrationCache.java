package io.github.leonardopinheirolacerda.akari.domain.cache.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.model.CacheEntry;
import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Optional;

/**
 * Cache genérico de chave/valor pra respostas de sistemas externos (AniList, TMDB), usado pelo
 * fluxo cache-first de {@link CacheUtils#resolve}.
 *
 * <p>Não tem relação com o cache local de arquivo de vídeo ({@code VideoFileCache}) — este aqui
 * guarda só o payload JSON de uma resposta upstream já buscada, sem baixar nem persistir arquivo
 * nenhum. O shape do payload varia por {@code cacheKey} (decidido por quem grava/lê, ex.:
 * {@code AnilistSearchService}/{@code TmdbService}), não é fixo pra entidade.
 */
@Entity
@Table(name = "integration_cache")
public class IntegrationCache extends PanacheEntityBase {

    /** Chave lógica da entrada, montada por quem grava (ex. via {@code CacheUtils.buildKey}). */
    @Id
    @Column(name = "cache_key")
    public String cacheKey;

    /** Resposta upstream serializada como JSON, no shape que {@code cacheKey} implica. */
    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode payload;

    /** Momento em que o payload foi buscado — usado pra calcular o TTL de frescor. */
    @Column(name = "fetched_at")
    public OffsetDateTime fetchedAt;

    /**
     * Busca uma entrada pela chave.
     *
     * @param cacheKey chave lógica da entrada
     * @return a entrada; vazio se nunca foi gravada
     */
    public static Optional<IntegrationCache> find(String cacheKey) {
        return IntegrationCache
                .find("cacheKey", cacheKey)
                .firstResultOptional();
    }

    /**
     * Busca e desserializa uma entrada pro tipo esperado por quem chama.
     *
     * @param cacheKey chave lógica da entrada
     * @param type tipo concreto pro qual desserializar o payload
     * @param objectMapper mapper usado na conversão de {@link JsonNode} pro tipo pedido
     * @param <T> tipo do valor cacheado
     * @return a entrada já desserializada, com o momento em que foi buscada; vazio se a chave
     *         nunca foi gravada
     */
    public static <T> Optional<CacheEntry<T>> readEntry(String cacheKey, Class<T> type, ObjectMapper objectMapper) {
        return IntegrationCache
                .find(cacheKey)
                .map(entry -> new CacheEntry<>(objectMapper.convertValue(entry.payload, type), entry.fetchedAt));
    }

    /**
     * Grava (cria ou substitui) o payload de uma chave, marcando {@code fetchedAt} como agora.
     *
     * @param cacheKey chave lógica da entrada
     * @param payload valor a serializar e gravar
     * @param objectMapper mapper usado na conversão do valor pra {@link JsonNode}
     */
    public static void writeEntry(String cacheKey, Object payload, ObjectMapper objectMapper) {
        final IntegrationCache entry = IntegrationCache.find(cacheKey).orElseGet(IntegrationCache::new);

        entry.cacheKey = cacheKey;
        entry.payload = objectMapper.valueToTree(payload);
        entry.fetchedAt = OffsetDateTime.now();

        entry.persist();
    }

}
