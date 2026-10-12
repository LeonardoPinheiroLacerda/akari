package io.github.leonardopinheirolacerda.akari.domain.mediafolder.model;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.utils.PaginationUtils;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Uma pasta do catalog (física ou lógica) dentro de um {@link StorageProvider}, persistida na
 * tabela {@code media_folder}. Forma uma árvore via {@code parent} — descendentes e arquivos
 * abrigados descem em cascata (DDL) quando a pasta é removida.
 */
@Entity
@Table(name = "media_folder")
public class MediaFolder extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_provider_id")
    public StorageProvider storageProvider;

    /** Pasta pai, derivada do {@code path} sem o último segmento; {@code null} se for raiz. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    public MediaFolder parent;

    @Enumerated(EnumType.STRING)
    @Column(name = "media_type")
    public MediaType mediaType;

    public String name;

    /** Caminho completo da pasta dentro do provider. */
    public String path;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /** Só pra filtro/sort de hasFiles (size(videoFiles)) — nenhuma leitura via cascata. */
    @OneToMany(mappedBy = "folder", fetch = FetchType.LAZY)
    public List<VideoFile> videoFiles = new ArrayList<>();

    /**
     * Busca uma pasta pelo id.
     *
     * @param id id da pasta
     * @return a pasta; vazio se não existir
     */
    public static Optional<MediaFolder> find(Integer id) {
        return MediaFolder
                .find("id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Busca a pasta pelo provider e path exato.
     *
     * @param storageProviderId id do provider
     * @param path path completo da pasta
     * @return a pasta; vazio se não existir
     */
    public static Optional<MediaFolder> findByProviderAndPath(Integer storageProviderId, String path) {
        return MediaFolder
                .find("storageProvider.id = ?1 and path = ?2", storageProviderId, path)
                .firstResultOptional();
    }

    /**
     * Busca todas as pastas do provider sob um subtree — a própria pasta do {@code rootPath}
     * (se existir como pasta) e tudo abaixo dela. {@code rootPath} vazio/{@code null} devolve a
     * árvore inteira do provider.
     *
     * @param storageProviderId id do storage provider
     * @param rootPath prefixo do subtree; vazio ou {@code null} para o provider inteiro
     * @return as pastas do subtree, em nenhuma ordem garantida
     */
    public static List<MediaFolder> findSubtree(Integer storageProviderId, String rootPath) {
        if (rootPath == null || rootPath.isBlank()) {
            return MediaFolder
                    .find("storageProvider.id = ?1", storageProviderId)
                    .list();
        }

        return MediaFolder
                .find("storageProvider.id = ?1 and (path = ?2 or path like ?2 || '/%')", storageProviderId, rootPath)
                .list();
    }

    /**
     * Pagina as pastas, aplicando os filtros informados como AND (cada parâmetro não-nulo
     * restringe o resultado) e a ordenação pedida.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param name substring case-insensitive do nome; {@code null} não filtra
     * @param mediaType tipo de mídia; {@code null} não filtra
     * @param storageProviderId id do provider; {@code null} não filtra
     * @param parentId id da pasta pai; {@code null} não filtra
     * @param hasFiles {@code true}/{@code false} filtra por ter ou não arquivos diretos;
     *                 {@code null} não filtra
     * @param sort specs {@code campo[:asc|desc]} na whitelist {@code name}/{@code createdAt};
     *             {@code null}/vazio usa a ordenação padrão
     * @return página de pastas que atendem aos filtros
     */
    public static PageResult<MediaFolder> findPage(
            Integer page,
            Integer size,
            String name,
            MediaType mediaType,
            Integer storageProviderId,
            Integer parentId,
            Boolean hasFiles,
            List<String> sort) {

        final List<String> clauses = new ArrayList<>();
        final Map<String, Object> params = new HashMap<>();

        if (name != null) {
            clauses.add("lower(name) like lower(:name)");
            params.put("name", "%" + name + "%");
        }
        if (mediaType != null) {
            clauses.add("mediaType = :mediaType");
            params.put("mediaType", mediaType);
        }
        if (storageProviderId != null) {
            clauses.add("storageProvider.id = :storageProviderId");
            params.put("storageProviderId", storageProviderId);
        }
        if (parentId != null) {
            clauses.add("parent.id = :parentId");
            params.put("parentId", parentId);
        }
        if (hasFiles != null) {
            clauses.add(
                    hasFiles 
                        ? "size(videoFiles) > 0" 
                        : "size(videoFiles) = 0"
            );
        }

        final String where = clauses.isEmpty()
                ? ""
                : "where " + String.join(" AND ", clauses);

        final Map<String, String> sortableFields = Map.of(
                "name", "name",
                "createdAt", "createdAt"
        );

        final PanacheQuery<MediaFolder> panacheQuery = MediaFolder
                .find(where, PaginationUtils.toSort(sort, sortableFields), params)
                .page(page, size);

        return new PageResult<>(
                panacheQuery.list(),
                page,
                size,
                panacheQuery.count(),
                panacheQuery.pageCount()
        );
    }

}
