package io.github.leonardopinheirolacerda.akari.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.utils.CacheEntry;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.Optional;

@Entity
@Table(name = "integration_cache")
public class IntegrationCache extends PanacheEntityBase {

    @Id
    @Column(name = "cache_key")
    public String cacheKey;

    @JdbcTypeCode(SqlTypes.JSON)
    public JsonNode payload;

    @Column(name = "fetched_at")
    public OffsetDateTime fetchedAt;

    public static Optional<IntegrationCache> find(String cacheKey) {
        return IntegrationCache
                .find("cacheKey", cacheKey)
                .firstResultOptional();
    }

    public static <T> Optional<CacheEntry<T>> readEntry(String cacheKey, Class<T> type, ObjectMapper objectMapper) {
        return IntegrationCache
                .find(cacheKey)
                .map(entry -> new CacheEntry<>(objectMapper.convertValue(entry.payload, type), entry.fetchedAt));
    }

    public static void writeEntry(String cacheKey, Object payload, ObjectMapper objectMapper) {
        final IntegrationCache entry = IntegrationCache.find(cacheKey).orElseGet(IntegrationCache::new);

        entry.cacheKey = cacheKey;
        entry.payload = objectMapper.valueToTree(payload);
        entry.fetchedAt = OffsetDateTime.now();

        entry.persist();
    }

}
