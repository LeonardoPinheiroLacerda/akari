package io.github.leonardopinheirolacerda.akari.domain.ingestion.sse;

import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStreamEvent;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantém os sinks SSE abertos de {@code /v1/ingestion/events} e distribui os eventos publicados
 * pelo {@code IngestionService} conforme as ingestões andam.
 *
 * <p>A instância de {@link Sse} só chega via {@code @Context} no método do resource que recebe
 * a subscription — por isso é capturada aqui no primeiro {@link #subscribe}, não injetada
 * diretamente neste bean {@code @ApplicationScoped}.
 */
@ApplicationScoped
public class IngestionEventBroadcaster {

    private final CopyOnWriteArrayList<SseEventSink> sinks = new CopyOnWriteArrayList<>();

    private volatile Sse sse;

    /**
     * Registra um novo sink SSE, mandando antes o {@code SNAPSHOT} com a ingestão em andamento
     * (ou a última). A conexão fica aberta mesmo sem ingestão em andamento, esperando a
     * próxima.
     *
     * @param sse instância do contexto SSE do resource, capturada pra montar eventos futuros
     * @param sink sink do cliente que acabou de se inscrever
     * @param snapshot evento {@code SNAPSHOT} a mandar primeiro
     */
    public void subscribe(Sse sse, SseEventSink sink, IngestionStreamEvent snapshot) {
        this.sse = sse;

        sink.send(toOutboundEvent(snapshot));
        sinks.add(sink);
    }

    /**
     * Confere se há algum cliente inscrito — sem nenhum, quem publica nem monta o evento.
     *
     * @return {@code true} se há algum sink aberto
     */
    public boolean hasSubscribers() {
        return !sinks.isEmpty();
    }

    /**
     * Distribui um evento pra todos os sinks abertos.
     *
     * <p>Não faz nada se não há inscritos. Remove da lista qualquer sink já fechado ou que
     * falhar o envio (desconexão do cliente).
     *
     * @param event evento a distribuir
     */
    public void publish(IngestionStreamEvent event) {
        if (sinks.isEmpty()) {
            return;
        }

        final OutboundSseEvent outboundEvent = toOutboundEvent(event);

        sinks.removeIf(sink -> {
            if (sink.isClosed()) {
                return true;
            }

            try {
                sink.send(outboundEvent);
                return false;
            } catch (Exception e) {
                Log.warnf(e, "Falha ao enviar evento de ingestão pro sink SSE — removendo");
                return true;
            }
        });
    }

    /**
     * Remove da lista os sinks já fechados pelo lado do cliente, sem esperar a próxima
     * publicação de evento — sem ingestão rodando, nada publica e um sink morto ficaria preso
     * na lista. Chamado periodicamente pelo {@code IngestionTaskScheduler}.
     *
     * @return a quantidade de sinks removidos
     */
    public int pruneClosedSinks() {
        final int before = sinks.size();

        sinks.removeIf(SseEventSink::isClosed);

        return before - sinks.size();
    }

    private OutboundSseEvent toOutboundEvent(IngestionStreamEvent event) {
        return sse.newEventBuilder()
                .name(event.getType().name())
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(IngestionStreamEvent.class, event)
                .build();
    }

}
