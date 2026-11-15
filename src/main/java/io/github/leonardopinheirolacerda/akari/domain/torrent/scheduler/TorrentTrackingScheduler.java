package io.github.leonardopinheirolacerda.akari.domain.torrent.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.torrent.service.TorrentTrackingService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Checa os animes rastreados e baixa os episódios novos a cada 15min
 * ({@link TorrentTrackingService#checkTrackedAnimes()}).
 *
 * <p>Cada execução é isolada por {@code ConcurrentExecution.SKIP} e protegida por try/catch — uma
 * falha num ciclo não derruba o agendamento.
 */
@ApplicationScoped
public class TorrentTrackingScheduler {

    @Inject
    TorrentTrackingService torrentTrackingService;

    @Scheduled(every = "15m", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledCheckTrackedAnimes() {
        try {
            torrentTrackingService.checkTrackedAnimes();
        } catch (Exception e) {
            Log.error("Checagem dos animes rastreados falhou", e);
        }
    }

}
