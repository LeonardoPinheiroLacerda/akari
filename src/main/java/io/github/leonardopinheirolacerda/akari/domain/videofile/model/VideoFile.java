package io.github.leonardopinheirolacerda.akari.domain.videofile.model;

import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Um arquivo de vídeo do catalog, persistido na tabela {@code video_file}, sempre abrigado por
 * uma {@link MediaFolder}. É sobre essa entidade que os domínios {@code cache} e
 * {@code mediaprobe} resolvem onde o arquivo está disponível em disco.
 */
@Entity
@Table(name = "video_file")
public class VideoFile extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "folder_id")
    public MediaFolder folder;

    public String name;

    /** Caminho completo do arquivo dentro do provider (pasta + nome). */
    public String path;

    @Column(name = "size_bytes")
    public Long sizeBytes;

    public String format;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Busca um arquivo pelo id.
     *
     * @param id id do arquivo
     * @return o arquivo; vazio se não existir
     */
    public static Optional<VideoFile> find(Integer id) {
        return VideoFile
                .find("id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Busca todos os arquivos de uma pasta, sem paginação — usado pela ingestão pra reconciliar
     * o conjunto inteiro de uma vez.
     *
     * @param folderId id da pasta no catalog
     * @return os arquivos da pasta
     */
    public static List<VideoFile> findByFolderId(Integer folderId) {
        return VideoFile
                .find("folder.id = ?1", folderId)
                .list();
    }

    /**
     * Pagina os arquivos de uma pasta.
     *
     * @param folderId id da pasta no catalog
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de arquivos da pasta; vazia se a pasta não existir ou não tiver arquivos
     */
    public static PageResult<VideoFile> findPage(Integer folderId, Integer page, Integer size) {
        final PanacheQuery<VideoFile> panacheQuery = VideoFile
                .find("folder.id = ?1", folderId)
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
