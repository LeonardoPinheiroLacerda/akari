package io.github.leonardopinheirolacerda.akari.domain.notification.service;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationPage;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCountResponse;
import io.github.leonardopinheirolacerda.akari.domain.notification.mapper.NotificationMapper;
import io.github.leonardopinheirolacerda.akari.domain.notification.model.Notification;
import io.github.leonardopinheirolacerda.akari.domain.notification.sse.NotificationEvent;
import io.github.leonardopinheirolacerda.akari.domain.notification.sse.NotificationEventBroadcaster;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

/**
 * Notificações exibidas ao usuário: listagem paginada e controle de lido/não-lido.
 */
@ApplicationScoped
public class NotificationService {

    @Inject
    NotificationMapper notificationMapper;

    @Inject
    NotificationEventBroadcaster notificationEventBroadcaster;

    /**
     * Pagina as notificações, da mais nova para a mais antiga.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de notificações, lidas ou não
     */
    public NotificationPage listNotifications(Integer page, Integer size) {
        final PageResult<Notification> notifications = Notification.findPage(page, size);

        return notificationMapper.toPage(notifications);
    }

    /**
     * Conta as notificações ainda não lidas.
     *
     * @return a contagem de notificações não-lidas
     */
    public UnreadCountResponse getUnreadCount() {
        final long count = Notification.countUnread();

        return new UnreadCountResponse((int) count);
    }

    /**
     * Marca uma notificação como lida.
     *
     * <p>Idempotente: não faz nada se a notificação já estiver lida, e não lança se o id não
     * existir — nesse caso a chamada simplesmente não altera nenhuma linha.
     *
     * @param notificationId id da notificação a marcar como lida
     */
    @Transactional
    public void markNotificationRead(Integer notificationId) {
        Notification
                .find(notificationId)
                .ifPresent(notification -> notification.read = true);
    }

    /**
     * Remove os sinks SSE de {@code /v1/notifications/events} já fechados pelo cliente.
     *
     * <p>Rotina periódica (ver {@code NotificationScheduler}), não ligada a nenhuma notificação
     * nova — sem ela, um sink morto só seria limpo na próxima chamada de {@code notify()}, que
     * pode demorar.
     */
    public void pruneClosedSseConnections() {
        final int removed = notificationEventBroadcaster.pruneClosedSinks();

        if (removed > 0) {
            Log.infof("%d conexões SSE de notificação encerradas removidas", removed);
        }
    }

    /**
     * Marca todas as notificações não lidas como lidas.
     */
    @Transactional
    public void markAllNotificationsRead() {
        final long updated = Notification.markAllAsRead();

        Log.infof("%d notificações marcadas como lidas", updated);
    }

    /**
     * Cria uma notificação e publica no stream SSE de {@code /v1/notifications/events}. Primeiro
     * produtor do projeto — hoje só a ingestão gera notificação (conclusão, falha geral e falhas
     * parciais do binding automático de cada pasta).
     *
     * @param level severidade
     * @param title título, ou {@code null}
     * @param message mensagem
     * @param folderId pasta relacionada, ou {@code null} se não houver uma
     * @param coverImageUrl capa pra exibição, ou {@code null}
     */
    @Transactional
    public void notify(NotificationLevel level, String title, String message, Integer folderId, String coverImageUrl) {
        final Notification notification = new Notification();
        notification.level = level;
        notification.title = title;
        notification.message = message;
        notification.folderId = folderId;
        notification.coverImageUrl = coverImageUrl;
        notification.read = false;

        notification.persist();

        final NotificationResponse notificationResponse = notificationMapper.toResponse(notification);

        final NotificationEvent created = NotificationEvent.created(notificationResponse);
        notificationEventBroadcaster.publish(created);

        Log.infof("Notificação %s criada: %s", level, message);
    }

}
