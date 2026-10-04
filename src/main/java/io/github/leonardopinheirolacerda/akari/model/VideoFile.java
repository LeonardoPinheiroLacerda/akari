package io.github.leonardopinheirolacerda.akari.model;

import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
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
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.Optional;

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

    public String path;

    @Column(name = "size_bytes")
    public Long sizeBytes;

    public String format;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    public static Optional<VideoFile> find(Integer id) {
        return VideoFile
                .find("id = ?1", id)
                .firstResultOptional();
    }

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
