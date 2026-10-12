package io.github.leonardopinheirolacerda.akari.domain.cache.sse;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantém os sinks SSE abertos de {@code /v1/cache/events} e distribui os eventos publicados
 * pelo {@code CacheService} conforme a fila de cache avança.
 *
 * <p>A instância de {@link Sse} só chega via {@code @Context} no método do resource que recebe
 * a subscription — por isso é capturada aqui no primeiro {@link #subscribe}, não injetada
 * diretamente neste bean {@code @ApplicationScoped}.
 */
@ApplicationScoped
public class CacheEventBroadcaster {

    private final CopyOnWriteArrayList<SseEventSink> sinks = new CopyOnWriteArrayList<>();

    private volatile Sse sse;

    /**
     * Registra um novo sink SSE e envia o estado inicial.
     *
     * <p>Sem nada ativo na fila, envia {@code IDLE} e fecha o sink na hora — nunca mantém uma
     * conexão aberta sem nada pra observar. Com algo ativo, envia o {@code SNAPSHOT} e mantém o
     * sink aberto pros próximos eventos.
     *
     * @param sse instância do contexto SSE do resource, capturada pra montar eventos futuros
     * @param sink sink do cliente que acabou de se inscrever
     * @param activeSnapshot linhas atualmente {@code QUEUED}/{@code DOWNLOADING}
     */
    public void subscribe(Sse sse, SseEventSink sink, List<CacheItem> activeSnapshot) {
        this.sse = sse;

        if (activeSnapshot.isEmpty()) {
            final OutboundSseEvent outboundEvent = toOutboundEvent(CacheEvent.idle());

            sink.send(outboundEvent);
            sink.close();

            return;
        }

        final CacheEvent snapshot = CacheEvent.snapshot(activeSnapshot);
        final OutboundSseEvent outboundEvent = toOutboundEvent(snapshot);

        sink.send(outboundEvent);
        sinks.add(sink);
    }

    /**
     * Distribui um evento pra todos os sinks abertos.
     *
     * <p>Não faz nada se não há inscritos. Remove da lista qualquer sink já fechado ou que
     * falhar o envio (desconexão do cliente).
     *
     * @param event evento a distribuir
     */
    public void publish(CacheEvent event) {
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
                Log.warnf(e, "Falha ao enviar evento de cache pro sink SSE — removendo");
                return true;
            }
        });
    }

    /**
     * Envia {@code IDLE} pra todos os sinks abertos, fecha cada um e esvazia a lista.
     *
     * <p>Aviso único de "fila esvaziou" — não é um heartbeat repetido. Chamado quando a fila de
     * cache transiciona de ter linhas ativas pra nenhuma.
     */
    public void publishIdleAndClose() {
        if (sinks.isEmpty()) {
            return;
        }

        final OutboundSseEvent idleEvent = toOutboundEvent(CacheEvent.idle());

        sinks.forEach(sink -> {
            try {
                sink.send(idleEvent);
            } catch (Exception e) {
                Log.warnf(e, "Falha ao enviar evento IDLE pro sink SSE");
            } finally {
                sink.close();
            }
        });

        sinks.clear();
    }

    /**
     * Confirma se há algum sink SSE aberto no momento.
     *
     * @return {@code true} se há ao menos um cliente inscrito
     */
    public boolean hasSubscribers() {
        return !sinks.isEmpty();
    }

    private OutboundSseEvent toOutboundEvent(CacheEvent event) {
        return sse.newEventBuilder()
                .name(event.type().name())
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(CacheEvent.class, event)
                .build();
    }

}
