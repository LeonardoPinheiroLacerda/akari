package io.github.leonardopinheirolacerda.akari.domain.notification.resource;

import io.github.leonardopinheirolacerda.akari.domain.notification.sse.NotificationEventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

/**
 * Stream SSE de notificações novas — não modelado no contrato OpenAPI (o gerador jaxrs-spec não
 * descreve Server-Sent Events), por isso é code-first, no mesmo molde de
 * {@code CacheEventResource}.
 */
@ApplicationScoped
@Path("/v1/notifications/events")
public class NotificationEventResource {

    @Inject
    NotificationEventBroadcaster notificationEventBroadcaster;

    /**
     * {@code GET /v1/notifications/events} — inscreve o cliente no stream de notificações novas.
     *
     * @param sseEventSink sink da conexão recém-aberta, injetado pelo JAX-RS
     * @param sse contexto SSE pra montar os eventos futuros, injetado pelo JAX-RS
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink sseEventSink, @Context Sse sse) {
        notificationEventBroadcaster.subscribe(sse, sseEventSink);
    }

}
