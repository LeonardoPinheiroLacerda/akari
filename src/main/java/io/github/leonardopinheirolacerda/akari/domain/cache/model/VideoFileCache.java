package io.github.leonardopinheirolacerda.akari.domain.cache.model;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Linha da fila de cache local de um {@link VideoFile} — download de um arquivo remoto pro
 * disco local, persistida na tabela {@code video_file_cache}.
 *
 * <p>O {@code id} é gerado no enfileiramento (não pelo banco) porque também nomeia o arquivo
 * em disco (ver {@code CacheService#queueCacheDownload}). Reenfileirar uma linha {@code FAILED}
 * reaproveita o mesmo {@code id} em vez de criar uma nova linha.
 */
@Entity
@Table(name = "video_file_cache")
public class VideoFileCache extends PanacheEntityBase {

    /** Também o nome do arquivo baixado em disco, dentro do diretório {@code cache.dir}. */
    @Id
    public UUID id;

    /** Arquivo do catalog sendo cacheado; removido em cascata se o arquivo for excluído. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    public VideoFile videoFile;

    /** Estado atual no ciclo {@code QUEUED → DOWNLOADING → (COMPLETED|FAILED)}. */
    @Enumerated(EnumType.STRING)
    public CacheStatus status;

    /** Id do job assíncrono do rclone; {@code null} fora de {@code DOWNLOADING}. */
    @Column(name = "job_id")
    public Long jobId;

    /** Momento em que a linha entrou (ou reentrou) na fila. */
    @Column(name = "queued_at")
    public OffsetDateTime queuedAt;

    /** Momento em que o download terminou; usado só pra ordenar a eviction LRU. */
    @Column(name = "fetched_at")
    public OffsetDateTime fetchedAt;

    /**
     * Busca uma linha pelo seu id.
     *
     * @param id id da linha (também o nome do arquivo em disco)
     * @return a linha; vazio se não existe
     */
    public static Optional<VideoFileCache> find(UUID id) {
        return VideoFileCache
                .find("id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Busca a linha de cache de um arquivo de vídeo, se existir.
     *
     * @param videoFileId id do {@link VideoFile} no catalog
     * @return a linha existente (em qualquer status); vazio se o arquivo nunca foi enfileirado
     */
    public static Optional<VideoFileCache> findByVideoFileId(Integer videoFileId) {
        return VideoFileCache
                .find("videoFile.id = ?1", videoFileId)
                .firstResultOptional();
    }

    /**
     * Busca as linhas atualmente baixando.
     *
     * @return as linhas {@code DOWNLOADING}; esperado no máximo uma, dado o slot serial da
     *         fila, mas a consulta não assume isso
     */
    public static List<VideoFileCache> findDownloading() {
        return VideoFileCache
                .find("status = ?1", CacheStatus.DOWNLOADING)
                .list();
    }

    /**
     * Busca a linha há mais tempo esperando na fila.
     *
     * @return a linha {@code QUEUED} mais antiga por {@code queuedAt}; vazio se a fila está vazia
     */
    public static Optional<VideoFileCache> findOldestQueued() {
        return VideoFileCache
                .find("status = ?1 order by queuedAt asc", CacheStatus.QUEUED)
                .firstResultOptional();
    }

    /**
     * Busca as linhas ainda não resolvidas (aguardando ou baixando).
     *
     * @return as linhas {@code QUEUED} ou {@code DOWNLOADING}, ordenadas por {@code queuedAt}
     */
    public static List<VideoFileCache> findActive() {
        return VideoFileCache
                .find("status in (?1, ?2) order by queuedAt asc", CacheStatus.QUEUED, CacheStatus.DOWNLOADING)
                .list();
    }

    /**
     * Conta as linhas ainda não resolvidas (aguardando ou baixando).
     *
     * @return quantidade de linhas {@code QUEUED} ou {@code DOWNLOADING}
     */
    public static long countActive() {
        return VideoFileCache
                .count("status in (?1, ?2)", CacheStatus.QUEUED, CacheStatus.DOWNLOADING);
    }

    /**
     * Busca as linhas já baixadas, da menos usada recentemente pra mais recente.
     *
     * @return as linhas {@code COMPLETED} ordenadas por {@code fetchedAt} ascendente — fila de
     *         vítimas da eviction LRU
     */
    public static List<VideoFileCache> findCompletedOrderByFetchedAtAsc() {
        return VideoFileCache
                .find("status = ?1 order by fetchedAt asc", CacheStatus.COMPLETED)
                .list();
    }

    /**
     * Pagina todas as linhas do cache, na ordem em que entraram na fila.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de linhas ordenadas por {@code queuedAt}
     */
    public static PageResult<VideoFileCache> findPage(Integer page, Integer size) {
        final PanacheQuery<VideoFileCache> query = VideoFileCache
                .find("order by queuedAt asc")
                .page(page, size);

        return new PageResult<>(
                query.list(),
                page,
                size,
                query.count(),
                query.pageCount()
        );
    }

    /**
     * Pagina as linhas ainda não concluídas, na ordem em que entraram na fila.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de linhas com status diferente de {@code COMPLETED}, ordenadas por
     *         {@code queuedAt} — inclui {@code FAILED}, já que uma linha falha pode ser
     *         reenfileirada
     */
    public static PageResult<VideoFileCache> findPendingPage(Integer page, Integer size) {
        final PanacheQuery<VideoFileCache> query = VideoFileCache
                .find("status != ?1 order by queuedAt asc", CacheStatus.COMPLETED)
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
