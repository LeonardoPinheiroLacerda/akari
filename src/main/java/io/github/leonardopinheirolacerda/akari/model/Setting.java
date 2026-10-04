package io.github.leonardopinheirolacerda.akari.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.common.Sort;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Entity
@Table(name = "setting")
public class Setting extends PanacheEntityBase {

    @Id
    public String key;

    public String value;

    public Boolean secret;

    @UpdateTimestamp
    @Column(name = "updated_at")
    public OffsetDateTime updatedAt;

    public boolean isConfigured() {
        return value != null && !value.isBlank();
    }

    public static Optional<Setting> find(String key) {
        return Setting
                .find("key = ?1", key)
                .firstResultOptional();
    }

    public static List<Setting> listAll() {
        return Setting.listAll(Sort.by("key"));
    }

    public static List<Setting> findByName(String name) {
        return Setting
                .find("lower(key) like lower(?1)", Sort.by("key"), "%" + name + "%")
                .list();
    }

}
