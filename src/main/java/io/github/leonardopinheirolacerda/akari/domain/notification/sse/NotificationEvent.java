package io.github.leonardopinheirolacerda.akari.domain.notification.sse;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;

/**
 * Payload de um evento do stream SSE {@code /v1/notifications/events}, publicado pelo
 * {@link NotificationEventBroadcaster}.
 *
 * @param type tipo do evento
 * @param notification notificação recém-criada
 */
public record NotificationEvent(
        NotificationEventType type,
        NotificationResponse notification
) {

    /**
     * Monta o evento de uma notificação recém-criada.
     *
     * @param notification notificação já convertida pro DTO de resposta
     * @return evento {@code NEW} com a notificação informada
     */
    public static NotificationEvent created(NotificationResponse notification) {
        return new NotificationEvent(NotificationEventType.NEW, notification);
    }

}
