package io.github.leonardopinheirolacerda.akari.domain.notification.resource;

import io.github.leonardopinheirolacerda.akari.api.NotificationApi;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCountResponse;
import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link NotificationApi} — notificações em {@code /v1/notifications}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no
 * {@link NotificationService}.
 */
@ApplicationScoped
public class NotificationResource implements NotificationApi {

    @Inject
    NotificationService notificationService;

    /**
     * {@code GET /v1/notifications/unread-count} — conta as notificações não-lidas.
     *
     * @return a contagem, com {@code 200 OK}
     */
    @Override
    public UnreadCountResponse getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    /**
     * {@code GET /v1/notifications} — lista as 100 notificações mais recentes, lidas ou não.
     *
     * @return as notificações, da mais nova para a mais antiga, com {@code 200 OK}
     */
    @Override
    public List<NotificationResponse> listNotifications() {
        return notificationService.listNotifications();
    }

    /**
     * {@code POST /v1/notifications/read-all} — marca todas as notificações como lidas.
     */
    @Override
    public void markAllNotificationsRead() {
        notificationService.markAllNotificationsRead();
    }

    /**
     * {@code POST /v1/notifications/{notificationId}/read} — marca uma notificação como lida.
     *
     * <p>Idempotente: marcar de novo uma já lida, ou um {@code notificationId} inexistente,
     * responde {@code 204} sem alterar nada.
     *
     * @param notificationId id da notificação a marcar como lida
     */
    @Override
    public void markNotificationRead(Integer notificationId) {
        notificationService.markNotificationRead(notificationId);
    }
}
