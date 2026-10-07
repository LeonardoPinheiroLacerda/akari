package io.github.leonardopinheirolacerda.akari.domain.notification.service;

import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCountResponse;
import io.github.leonardopinheirolacerda.akari.domain.notification.mapper.NotificationMapper;
import io.github.leonardopinheirolacerda.akari.domain.notification.model.Notification;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;

@ApplicationScoped
public class NotificationService {

    private static final int RECENT_LIMIT = 100;

    @Inject
    NotificationMapper notificationMapper;

    public List<NotificationResponse> listNotifications() {
        final List<Notification> notifications = Notification.listRecent(RECENT_LIMIT);

        Log.infof("Listadas %d notificações", notifications.size());

        return notificationMapper.toResponseList(notifications);
    }

    public UnreadCountResponse getUnreadCount() {
        final long count = Notification.countUnread();

        return new UnreadCountResponse((int) count);
    }

    @Transactional
    public void markNotificationRead(Integer notificationId) {
        Notification
                .find(notificationId)
                .ifPresent(notification -> notification.read = true);
    }

    @Transactional
    public void markAllNotificationsRead() {
        final long updated = Notification.markAllAsRead();

        Log.infof("%d notificações marcadas como lidas", updated);
    }

}
