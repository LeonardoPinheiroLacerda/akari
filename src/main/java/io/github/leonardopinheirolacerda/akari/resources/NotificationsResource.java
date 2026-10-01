package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.NotificationsApi;
import io.github.leonardopinheirolacerda.akari.api.dto.Notification;
import io.github.leonardopinheirolacerda.akari.api.dto.UnreadCount;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class NotificationsResource implements NotificationsApi {

    @Override
    public UnreadCount getUnreadCount() {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public List<Notification> listNotifications() {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void markAllNotificationsRead() {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // notificationId inexistente NÃO é 404: responde 204 sem alterar nada (idempotente).
    @Override
    public void markNotificationRead(Integer notificationId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
