package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.CacheApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItemPage;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheQueueRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class CacheResource implements CacheApi {

    @Override
    public CacheItemPage listCaches(Integer page, Integer size) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public CacheItemPage listPendingCaches(Integer page, Integer size) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 400 BUSINESS_RULE_VIOLATION: arquivo já QUEUED, DOWNLOADING ou COMPLETED (FAILED pode ser
    // reenfileirado), ou arquivo de provider LOCAL (lido direto do disco, sem cache).
    // 404: fileId inexistente no catalog.
    @Override
    public CacheItem queueCacheDownload(CacheQueueRequest cacheQueueRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
