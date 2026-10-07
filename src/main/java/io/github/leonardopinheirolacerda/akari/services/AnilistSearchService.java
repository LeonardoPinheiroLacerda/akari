package io.github.leonardopinheirolacerda.akari.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.clients.anilist.AnilistClient;
import io.github.leonardopinheirolacerda.akari.clients.anilist.AnilistQueries;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistGraphqlRequest;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistMediaQueryResponse;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistPageResponse;
import io.github.leonardopinheirolacerda.akari.clients.anilist.dtos.AnilistSearchResponse;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.mapper.AnilistMapper;
import io.github.leonardopinheirolacerda.akari.model.IntegrationCache;
import io.github.leonardopinheirolacerda.akari.model.Setting;
import io.github.leonardopinheirolacerda.akari.utils.CacheEntry;
import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AnilistSearchService {

    @Inject
    @RestClient
    AnilistClient anilistClient;

    @Inject
    AnilistMapper mapper;

    @Inject
    SettingService settingService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public AnilistMediaSummaryPage search(String query, Integer page, Integer size, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey(query, String.valueOf(page), String.valueOf(size));

        return CacheUtils.resolve(
                forceRefresh,
                anilistCacheTtl(),
                () -> readCache(cacheKey, AnilistMediaSummaryPage.class),
                () -> fetchSearchFromAnilist(query, page, size),
                payload -> writeCache(cacheKey, payload)
        );
    }

    @Transactional
    public AnilistMediaResponse findById(Integer anilistId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("media", String.valueOf(anilistId));

        return CacheUtils.resolve(
                forceRefresh,
                anilistCacheTtl(),
                () -> readCache(cacheKey, AnilistMediaResponse.class),
                () -> fetchMediaFromAnilist(anilistId),
                payload -> writeCache(cacheKey, payload)
        );
    }

    private AnilistMediaSummaryPage fetchSearchFromAnilist(String query, Integer page, Integer size) {
        final AnilistGraphqlRequest request = new AnilistGraphqlRequest(
                AnilistQueries.MEDIA_SEARCH,
                Map.of(
                        "search", query,
                        "page", page + 1,
                        "perPage", size
                )
        );

        final AnilistSearchResponse response;

        try {
            response = anilistClient.search(request);

        } catch (ProcessingException | WebApplicationException e) {
            Log.errorf(e, "Busca na AniList falhou para \"%s\"", query);
            throw new IntegrationException("Busca na AniList falhou", e);
        }

        final AnilistPageResponse page1Based = response.data().page();

        return new AnilistMediaSummaryPage()
                .page(page)
                .size(size)
                .totalElements(page1Based.pageInfo().total().longValue())
                .totalPages(page1Based.pageInfo().lastPage())
                .data(mapper.toSummaryList(page1Based.media()));
    }

    private AnilistMediaResponse fetchMediaFromAnilist(Integer anilistId) {
        final AnilistGraphqlRequest request = new AnilistGraphqlRequest(
                AnilistQueries.MEDIA_BY_ID,
                Map.of("id", anilistId)
        );

        final AnilistMediaQueryResponse response;

        try {
            response = anilistClient.findById(request);

        } catch (ProcessingException | WebApplicationException e) {
            Log.errorf(e, "Busca na AniList falhou para o id %d", anilistId);
            throw new IntegrationException("Busca na AniList falhou", e);
        }

        return response.data().media();
    }

    private Duration anilistCacheTtl() {
        final Setting setting = settingService.findOrThrow("anilist.cache.ttl");
        return Duration.parse(setting.value);
    }

    private <T> Optional<CacheEntry<T>> readCache(String cacheKey, Class<T> type) {
        return IntegrationCache.find(cacheKey)
                .map(entry -> {
                    final T cache = objectMapper.convertValue(entry.payload, type);
                    return new CacheEntry<>(cache, entry.fetchedAt);
                });
    }

    private void writeCache(String cacheKey, Object payload) {
        final IntegrationCache entry = IntegrationCache.find(cacheKey).orElseGet(IntegrationCache::new);

        entry.cacheKey = cacheKey;
        entry.payload = objectMapper.valueToTree(payload);
        entry.fetchedAt = OffsetDateTime.now();

        entry.persist();
    }

}
