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

@Entity
@Table(name = "video_file_cache")
public class VideoFileCache extends PanacheEntityBase {

    @Id
    public UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id")
    public VideoFile videoFile;

    @Enumerated(EnumType.STRING)
    public CacheStatus status;

    @Column(name = "job_id")
    public Long jobId;

    @Column(name = "queued_at")
    public OffsetDateTime queuedAt;

    @Column(name = "fetched_at")
    public OffsetDateTime fetchedAt;

    public static Optional<VideoFileCache> find(UUID id) {
        return VideoFileCache
                .find("id = ?1", id)
                .firstResultOptional();
    }

    public static Optional<VideoFileCache> findByVideoFileId(Integer videoFileId) {
        return VideoFileCache
                .find("videoFile.id = ?1", videoFileId)
                .firstResultOptional();
    }

    public static List<VideoFileCache> findDownloading() {
        return VideoFileCache
                .find("status = ?1", CacheStatus.DOWNLOADING)
                .list();
    }

    public static Optional<VideoFileCache> findOldestQueued() {
        return VideoFileCache
                .find("status = ?1 order by queuedAt asc", CacheStatus.QUEUED)
                .firstResultOptional();
    }

    public static List<VideoFileCache> findActive() {
        return VideoFileCache
                .find("status in (?1, ?2) order by queuedAt asc", CacheStatus.QUEUED, CacheStatus.DOWNLOADING)
                .list();
    }

    public static long countActive() {
        return VideoFileCache
                .count("status in (?1, ?2)", CacheStatus.QUEUED, CacheStatus.DOWNLOADING);
    }

    public static List<VideoFileCache> findCompletedOrderByFetchedAtAsc() {
        return VideoFileCache
                .find("status = ?1 order by fetchedAt asc", CacheStatus.COMPLETED)
                .list();
    }

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
