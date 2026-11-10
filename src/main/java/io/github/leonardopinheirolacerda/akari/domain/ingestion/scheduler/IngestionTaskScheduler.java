package io.github.leonardopinheirolacerda.akari.domain.ingestion.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionTaskExecutionService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Dispara a execução das tasks de ingestão ({@link IngestionTaskExecutionService}), uma por
 * ciclo, a cada 1s, e a limpeza das conexões SSE fechadas do stream de ingestão
 * ({@link IngestionService}), a cada 60s.
 *
 * <p>{@code ConcurrentExecution.SKIP} faz o ciclo que cai durante uma task em andamento ser
 * pulado. Cada ciclo é protegido por try/catch — uma falha não derruba o agendamento.
 */
@ApplicationScoped
public class IngestionTaskScheduler {

    @Inject
    IngestionTaskExecutionService ingestionTaskExecutionService;

    @Inject
    IngestionService ingestionService;

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledExecuteNextTask() {
        try {
            ingestionTaskExecutionService.executeNextTask();
        } catch (Exception e) {
            Log.error("Execução da tarefa de ingestão falhou", e);
        }
    }

    @Scheduled(every = "60s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPruneClosedSseConnections() {
        try {
            ingestionService.pruneClosedSseConnections();
        } catch (Exception e) {
            Log.error("Limpeza de conexões SSE de ingestão falhou", e);
        }
    }
}
