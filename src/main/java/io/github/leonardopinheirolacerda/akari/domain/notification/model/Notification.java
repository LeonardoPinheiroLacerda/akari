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

/**
 * Uma notificação exibida ao usuário, persistida na tabela {@code notification}.
 *
 * <p>Produzida hoje só pela ingestão (conclusão, falha geral e falhas parciais do binding
 * automático de cada pasta, ver {@code NotificationService#notify}). Sem paginação na API —
 * só as 100 mais recentes são consultáveis.
 */
@Entity
@Table(name = "notification")
public class Notification extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Integer id;

    /** Severidade da notificação. */
    @Enumerated(EnumType.STRING)
    public NotificationLevel level;

    public String message;

    /** Pasta do catalog relacionada, ou {@code null} se a notificação não for sobre uma pasta. */
    @Column(name = "folder_id")
    public Integer folderId;

    /** Título, ou {@code null}. */
    public String title;

    /** URL de capa pra exibição, ou {@code null}. */
    @Column(name = "cover_image_url")
    public String coverImageUrl;

    /** {@code true} depois de marcada como lida. */
    @Column(name = "is_read")
    public Boolean read;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    public OffsetDateTime createdAt;

    /**
     * Busca uma notificação pelo id.
     *
     * @param id id da notificação
     * @return a notificação; vazio se não existir
     */
    public static Optional<Notification> find(Integer id) {
        return Notification
                .find("id = ?1", id)
                .firstResultOptional();
    }

    /**
     * Busca as notificações mais recentes, lidas ou não.
     *
     * @param limit quantidade máxima de notificações a devolver
     * @return as notificações, da mais nova para a mais antiga
     */
    public static List<Notification> listRecent(int limit) {
        return Notification
                .findAll(Sort.descending("createdAt"))
                .page(0, limit)
                .list();
    }

    /**
     * Conta as notificações ainda não lidas.
     *
     * @return a quantidade de notificações com {@code read = false}
     */
    public static long countUnread() {
        return Notification.count("read = false");
    }

    /**
     * Marca todas as notificações não lidas como lidas.
     *
     * @return a quantidade de linhas atualizadas
     */
    public static long markAllAsRead() {
        return Notification.update("set read = true where read = false");
    }

}
