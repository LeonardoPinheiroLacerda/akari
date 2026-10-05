package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.NotificationApi;
import io.github.leonardopinheirolacerda.akari.api.dto.NotificationResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCountResponse;
import io.github.leonardopinheirolacerda.akari.services.NotificationService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class NotificationResource implements NotificationApi {

    @Inject
    NotificationService notificationService;

    @Override
    public UnreadCountResponse getUnreadCount() {
        return notificationService.getUnreadCount();
    }

    @Override
    public List<NotificationResponse> listNotifications() {
        return notificationService.listNotifications();
    }

    @Override
    public void markAllNotificationsRead() {
        notificationService.markAllNotificationsRead();
    }

    @Override
    public void markNotificationRead(Integer notificationId) {
        notificationService.markNotificationRead(notificationId);
    }
}
