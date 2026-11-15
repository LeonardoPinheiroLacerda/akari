package io.github.leonardopinheirolacerda.akari.domain.torrent.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.torrent.service.TorrentDownloadService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Sincroniza os downloads em andamento com o qBittorrent a cada 10s
 * ({@link TorrentDownloadService#syncDownloads()}).
 *
 * <p>Cada execução é isolada por {@code ConcurrentExecution.SKIP} e protegida por try/catch — uma
 * falha num ciclo (ex.: qBittorrent fora do ar) não derruba o agendamento.
 */
@ApplicationScoped
public class TorrentDownloadScheduler {

    @Inject
    TorrentDownloadService torrentDownloadService;

    @Scheduled(every = "10s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledSyncDownloads() {
        try {
            torrentDownloadService.syncDownloads();
        } catch (Exception e) {
            Log.error("Sincronização dos downloads de torrent falhou", e);
        }
    }

}
