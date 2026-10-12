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

/**
 * Fonte de arquivos de vídeo persistida na tabela {@code storage_provider} — local (disco do
 * servidor) ou remota (ex.: MEGA, via rclone).
 *
 * <p>{@code config} guarda os campos exigidos pelo {@code type} (ver {@code LocalConfig}/
 * {@code MegaConfig}) como JSON livre; campos marcados {@code @Obscure} nesses records chegam
 * aqui já obscurecidos pelo rclone, nunca em claro.
 */
@Entity
@Table(name = "storage_provider")
public class StorageProvider extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    /** Tipo do provider; decide o schema de {@code config} e o client resolvido em runtime. */
    @Enumerated(EnumType.STRING)
    public StorageProviderType type;

    /** Config específica do {@code type}, com os campos sensíveis já obscurecidos. */
    @JdbcTypeCode(SqlTypes.JSON)
    public Map<String, String> config;

    @CreationTimestamp
    @Column(name = "created_at")
    public OffsetDateTime createdAt;

    /**
     * Busca um provider pelo id.
     *
     * @param id id do provider
     * @return o provider; vazio se não existe
     */
    public static Optional<StorageProvider> find(Integer id) {
        return StorageProvider
                .find("id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Pagina todos os providers cadastrados.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de providers
     */
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