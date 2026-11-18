package io.github.leonardopinheirolacerda.akari.domain.anime.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingFillService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Dispara a cada 6 horas {@code AnimeTmdbFilePairingFillService.refreshRecentEpisodes}.
 */
@ApplicationScoped
public class AnimeTmdbEpisodeRefreshScheduler {

    @Inject
    AnimeTmdbFilePairingFillService animeTmdbFilePairingFillService;

    @Scheduled(every = "6h", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledRefreshRecentEpisodes() {
        try {
            animeTmdbFilePairingFillService.refreshRecentEpisodes();
        } catch (Exception e) {
            Log.error("Pairing TMDB dos episódios recentes falhou", e);
        }
    }
}
