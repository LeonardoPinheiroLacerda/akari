package io.github.leonardopinheirolacerda.akari.domain.cache.resource;

import io.github.leonardopinheirolacerda.akari.api.CacheApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItemPage;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheQueueRequest;
import io.github.leonardopinheirolacerda.akari.domain.cache.service.CacheService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class CacheResource implements CacheApi {

    @Inject
    CacheService cacheService;

    @Override
    public CacheItemPage listCaches(Integer page, Integer size) {
        return cacheService.listCaches(page, size);
    }

    @Override
    public CacheItemPage listPendingCaches(Integer page, Integer size) {
        return cacheService.listPendingCaches(page, size);
    }

    @Override
    public CacheItem queueCacheDownload(CacheQueueRequest cacheQueueRequest) {
        return cacheService.queueCacheDownload(cacheQueueRequest);
    }
}
