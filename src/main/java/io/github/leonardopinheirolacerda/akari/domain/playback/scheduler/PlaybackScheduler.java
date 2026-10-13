package io.github.leonardopinheirolacerda.akari.domain.playback.scheduler;

import io.github.leonardopinheirolacerda.akari.domain.playback.service.PlaybackService;
import io.quarkus.logging.Log;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Dispara a varredura de sessões de playback expiradas ({@link PlaybackService#sweepExpiredSessions})
 * a cada 5s — mesma cadência do {@code CacheScheduler}.
 */
@ApplicationScoped
public class PlaybackScheduler {

    @Inject
    PlaybackService playbackService;

    @Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    void scheduledSweepExpiredSessions() {
        try {
            playbackService.sweepExpiredSessions();
        } catch (Exception e) {
            Log.error("Varredura de sessões de playback expiradas falhou", e);
        }
    }

}
