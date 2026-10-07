package io.github.leonardopinheirolacerda.akari.domain.storageprovider.model;

import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "storage_provider")
public class StorageProvider extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Enumerated(EnumType.STRING)
    public StorageProviderType type;

    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, String> config;

    @CreationTimestamp
    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    public static Optional<StorageProvider> find(Integer id) {
        return StorageProvider
                .find("id = ?1", id)
                .firstResultOptional();
    }

    public static PageResult<StorageProvider> findPage(Integer page, Integer size) {
        final PanacheQuery<StorageProvider> query = StorageProvider
                .findAll()
                .page(page, size);

        return new PageResult<>(
                query.list(), 
                page, 
                size, 
                query.count(), 
                query.pageCount()
        );
    }

}