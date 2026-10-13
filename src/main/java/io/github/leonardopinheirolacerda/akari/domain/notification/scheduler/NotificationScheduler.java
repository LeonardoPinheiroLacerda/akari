package io.github.leonardopinheirolacerda.akari.domain.notification.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.notification.service.NotificationService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Limpa periodicamente os sinks SSE de {@code /v1/notifications/events} já fechados pelo
 * cliente. Diferente do {@code cache}, este domínio não tem fila/download que force essa
 * limpeza como efeito colateral de atividade própria — só a chamada de {@code notify()}
 * mexeria na lista de sinks, e notificações podem ser raras — por isso precisa de um
 * agendamento dedicado.
 */
@ApplicationScoped
public class NotificationScheduler {

    @Inject
    NotificationService notificationService;

    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPruneClosedSseConnections() {
        try {
            notificationService.pruneClosedSseConnections();
        } catch (Exception e) {
            Log.error("Limpeza de conexões SSE de notificação falhou", e);
        }
    }

}