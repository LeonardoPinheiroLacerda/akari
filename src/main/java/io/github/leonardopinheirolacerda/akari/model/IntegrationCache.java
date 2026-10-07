package io.github.leonardopinheirolacerda.akari.model;

import com.fasterxml.jackson.databind.JsonNode;
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

}
