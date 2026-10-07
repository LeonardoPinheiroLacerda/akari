package io.github.leonardopinheirolacerda.akari.domain.notification.model;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "notification")
public class Notification extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    @Enumerated(EnumType.STRING)
    public NotificationLevel level;

    public String message;

    @Column(name = "folder_id")
    public Integer folderId;

    public String title;

    @Column(name = "cover_image_url")
    public String coverImageUrl;

    @Column(name = "is_read")
    public Boolean read;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    public static Optional<Notification> find(Integer id) {
        return Notification
                .find("id = ?1", id)
                .firstResultOptional();
    }

    public static List<Notification> listRecent(int limit) {
        return Notification
                .findAll(Sort.descending("createdAt"))
                .page(0, limit)
                .list();
    }

    public static long countUnread() {
        return Notification.count("read = false");
    }

    public static long markAllAsRead() {
        return Notification.update("set read = true where read = false");
    }

}
