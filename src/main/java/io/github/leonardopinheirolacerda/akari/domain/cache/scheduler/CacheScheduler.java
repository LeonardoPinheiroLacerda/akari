package io.github.leonardopinheirolacerda.akari.domain.cache.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.cache.service.CacheQueueProcessorService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Dispara o motor da fila de cache de vídeo ({@link CacheQueueProcessorService}) em dois
 * ritmos: processamento da fila a cada 5s, progresso do download ativo a cada 1s.
 *
 * <p>Cada execução é isolada por {@code ConcurrentExecution.SKIP} e protegida por try/catch —
 * uma falha num ciclo não derruba o agendamento nem propaga como exceção não tratada.
 */
@ApplicationScoped
public class CacheScheduler {

    @Inject
    CacheQueueProcessorService cacheQueueProcessorService;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledProcessQueue() {
        try {
            cacheQueueProcessorService.processQueue();
        } catch (Exception e) {
            Log.error("Processamento da fila de cache falhou", e);
        }
    }

    @Scheduled(every = "1s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledPollProgress() {
        try {
            cacheQueueProcessorService.pollProgress();
        } catch (Exception e) {
            Log.error("Consulta de progresso do cache falhou", e);
        }
    }

}
