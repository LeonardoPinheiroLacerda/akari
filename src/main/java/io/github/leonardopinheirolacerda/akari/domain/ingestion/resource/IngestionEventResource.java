package io.github.leonardopinheirolacerda.akari.domain.ingestion.resource;

import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStreamEvent;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.sse.IngestionEventBroadcaster;
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
 * Stream SSE do andamento das ingestões — o endpoint não é descrito em {@code paths} do
 * contrato OpenAPI (o gerador jaxrs-spec não descreve Server-Sent Events), por isso é
 * code-first, no mesmo molde de {@code CacheEventResource}. O payload de cada evento é o DTO
 * gerado {@code IngestionStreamEvent} (schema do contrato).
 */
@ApplicationScoped
@Path("/v1/ingestion/events")
public class IngestionEventResource {

    @Inject
    IngestionService ingestionService;

    @Inject
    IngestionEventBroadcaster ingestionEventBroadcaster;

    /**
     * {@code GET /v1/ingestion/events} — inscreve o cliente no stream das ingestões.
     *
     * <p>Monta o {@code SNAPSHOT} da ingestão em andamento (ou da última) e delega o ciclo de
     * vida da conexão pro {@link IngestionEventBroadcaster}.
     *
     * @param sseEventSink sink da conexão recém-aberta, injetado pelo JAX-RS
     * @param sse contexto SSE pra montar os eventos futuros, injetado pelo JAX-RS
     */
    @GET
    @Produces(MediaType.SERVER_SENT_EVENTS)
    public void subscribe(@Context SseEventSink sseEventSink, @Context Sse sse) {
        final IngestionStreamEvent snapshot = ingestionService.getSnapshot();

        ingestionEventBroadcaster.subscribe(sse, sseEventSink, snapshot);
    }

}
