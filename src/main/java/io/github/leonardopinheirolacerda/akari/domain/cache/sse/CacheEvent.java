package io.github.leonardopinheirolacerda.akari.domain.cache.sse;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import java.util.List;
import java.util.UUID;

public record CacheEvent(
        CacheEventType type,
        UUID id,
        CacheStatus status,
        String errorMessage,
        Long bytes,
        Long totalBytes,
        Double speed,
        Long eta,
        Double percentage,
        List<CacheItem> items
) {

    public static CacheEvent snapshot(List<CacheItem> items) {
        return new CacheEvent(CacheEventType.SNAPSHOT, null, null, null, null, null, null, null, null, items);
    }

    public static CacheEvent idle() {
        return new CacheEvent(CacheEventType.IDLE, null, null, null, null, null, null, null, null, null);
    }

    public static CacheEvent queued(UUID id) {
        return new CacheEvent(CacheEventType.QUEUED, id, CacheStatus.QUEUED, null, null, null, null, null, null, null);
    }

    public static CacheEvent started(UUID id) {
        return new CacheEvent(CacheEventType.STARTED, id, CacheStatus.DOWNLOADING, null, null, null, null, null, null, null);
    }

    public static CacheEvent progress(UUID id, Long bytes, Long totalBytes, Double speed, Long eta, Double percentage) {
        return new CacheEvent(CacheEventType.PROGRESS, id, CacheStatus.DOWNLOADING, null, bytes, totalBytes, speed, eta, percentage, null);
    }

    public static CacheEvent completed(UUID id) {
        return new CacheEvent(CacheEventType.COMPLETED, id, CacheStatus.COMPLETED, null, null, null, null, null, null, null);
    }

    public static CacheEvent failed(UUID id, String errorMessage) {
        return new CacheEvent(CacheEventType.FAILED, id, CacheStatus.FAILED, errorMessage, null, null, null, null, null, null);
    }

    public static CacheEvent evicted(UUID id) {
        return new CacheEvent(CacheEventType.EVICTED, id, null, null, null, null, null, null, null, null);
    }

}
