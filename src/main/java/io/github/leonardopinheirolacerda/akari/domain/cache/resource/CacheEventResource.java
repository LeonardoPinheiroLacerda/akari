package io.github.leonardopinheirolacerda.akari.domain.cache.resource;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.domain.cache.service.CacheService;
import io.github.leonardopinheirolacerda.akari.domain.cache.sse.CacheEventBroadcaster;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.List;

/**
 * Stream SSE de progresso do cache de vídeo — não modelado no contrato OpenAPI (o gerador
 * jaxrs-spec não descreve Server-Sent Events), por isso é code-first.
 */
@ApplicationScoped
@Path("/v1/cache/events")
public class CacheEventResource {

    @Inject
    CacheService cacheService;

    @Inject
    CacheEventBroadcaster cacheEventBroadcaster;

    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink sseEventSink, @Context Sse sse) {
        final List<CacheItem> activeSnapshot = cacheService.listActiveCaches();
        cacheEventBroadcaster.subscribe(sse, sseEventSink, activeSnapshot);
    }

}
