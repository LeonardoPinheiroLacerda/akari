package io.github.leonardopinheirolacerda.akari.domain.notification.sse;

import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.sse.OutboundSseEvent;
import jakarta.ws.rs.sse.Sse;
import jakarta.ws.rs.sse.SseEventSink;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Mantém os sinks SSE abertos de {@code /v1/notifications/events} e distribui os eventos
 * publicados pelo {@code NotificationService} quando uma notificação nova é criada.
 *
 * <p>A instância de {@link Sse} só chega via {@code @Context} no método do resource que recebe
 * a subscription — por isso é capturada aqui no primeiro {@link #subscribe}, não injetada
 * diretamente neste bean {@code @ApplicationScoped}.
 */
@ApplicationScoped
public class NotificationEventBroadcaster {

    private final CopyOnWriteArrayList<SseEventSink> sinks = new CopyOnWriteArrayList<>();

    private volatile Sse sse;

    /**
     * Registra um novo sink SSE. Diferente do stream de {@code cache}, aqui não há um estado
     * "ativo" pra fazer snapshot — a conexão só fica esperando os próximos eventos.
     *
     * @param sse instância do contexto SSE do resource, capturada pra montar eventos futuros
     * @param sink sink do cliente que acabou de se inscrever
     */
    public void subscribe(Sse sse, SseEventSink sink) {
        this.sse = sse;

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
    public void publish(NotificationEvent event) {
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
                Log.warnf(e, "Falha ao enviar evento de notificação pro sink SSE — removendo");
                return true;
            }
        });
    }

    /**
     * Remove da lista os sinks já fechados pelo lado do cliente, sem esperar a próxima
     * publicação de evento.
     *
     * <p>Diferente do {@code cache}, este domínio não tem fila/download que force essa limpeza
     * como efeito colateral de atividade própria — sem isso, um sink morto entre duas chamadas
     * de {@link #publish} ficaria preso na lista indefinidamente. Chamado periodicamente pelo
     * {@code NotificationScheduler}.
     *
     * @return a quantidade de sinks removidos
     */
    public int pruneClosedSinks() {
        final int before = sinks.size();

        sinks.removeIf(SseEventSink::isClosed);

        return before - sinks.size();
    }

    private OutboundSseEvent toOutboundEvent(NotificationEvent event) {
        return sse.newEventBuilder()
                .name(event.type().name())
                .mediaType(MediaType.APPLICATION_JSON_TYPE)
                .data(NotificationEvent.class, event)
                .build();
    }

}
