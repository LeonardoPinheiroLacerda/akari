package io.github.leonardopinheirolacerda.akari.domain.cache.sse;

public enum CacheEventType {
    SNAPSHOT,
    IDLE,
    QUEUED,
    STARTED,
    PROGRESS,
    COMPLETED,
    FAILED,
    EVICTED
}
