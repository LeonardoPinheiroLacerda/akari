package io.github.leonardopinheirolacerda.akari.domain.notification.service;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationLevel;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCountResponse;
import io.github.leonardopinheirolacerda.akari.domain.notification.mapper.NotificationMapper;
import io.github.leonardopinheirolacerda.akari.domain.notification.model.Notification;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

/**
 * Notificações exibidas ao usuário: listagem das mais recentes e controle de lido/não-lido.
 */
@ApplicationScoped
public class NotificationService {

    private static final int RECENT_LIMIT = 100;

    @Inject
    NotificationMapper notificationMapper;

    /**
     * Lista as notificações mais recentes, lidas ou não.
     *
     * @return até 100 notificações, da mais nova para a mais antiga
     */
    public List<NotificationResponse> listNotifications() {
        final List<Notification> notifications = Notification.listRecent(RECENT_LIMIT);

        Log.infof("Listadas %d notificações", notifications.size());

        return notificationMapper.toResponseList(notifications);
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
     * Marca todas as notificações não lidas como lidas.
     */
    @Transactional
    public void markAllNotificationsRead() {
        final long updated = Notification.markAllAsRead();

        Log.infof("%d notificações marcadas como lidas", updated);
    }

    /**
     * Cria uma notificação. Primeiro produtor do projeto — hoje só a ingestão gera notificação
     * (conclusão, falha geral e falhas parciais do binding automático de cada pasta).
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

        Log.infof("Notificação %s criada: %s", level, message);
    }

}
