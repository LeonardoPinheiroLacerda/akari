package io.github.leonardopinheirolacerda.akari.domain.anibridge.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeService;
import io.quarkus.logging.Log;
import io.quarkus.runtime.StartupEvent;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Dispara o {@link AnibridgeService#refresh()} no boot e de hora em hora.
 *
 * <p>O akari é de uso pessoal e não fica rodando 24/7, então um horário fixo (ex.: alinhado com
 * o cron upstream de 02:00 UTC) dificilmente coincidiria com a aplicação de pé. Como
 * {@code refresh()} já é barato quando não há mudança (só compara {@code upstreamUpdatedAt}, sem
 * baixar nada), rodar de hora em hora é mais confiável pra captar a atualização diária sempre
 * que a aplicação estiver ligada.
 */
@ApplicationScoped
public class AnibridgeScheduler {

    @Inject
    AnibridgeService anibridgeService;

    @Scheduled(every = "1h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefresh() {
        try {
            anibridgeService.refresh();
        } catch (Exception e) {
            Log.error("Refresh agendado do anibridge falhou", e);
        }
    }

    void onStart(@Observes StartupEvent event) {
        Thread.ofVirtual().start(() -> {
            try {
                anibridgeService.refresh();
            } catch (Exception e) {
                Log.error("Refresh do anibridge no boot falhou", e);
            }
        });
    }

}
