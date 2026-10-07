package io.github.leonardopinheirolacerda.akari.domain.anilist.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.AnilistClient;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.AnilistQueries;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistGraphqlRequest;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaQueryResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistPageResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistSearchResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.mapper.AnilistMapper;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.IntegrationCache;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
import io.github.leonardopinheirolacerda.akari.utils.HttpErrorUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.Map;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class AnilistSearchService {

    @Inject
    @RestClient
    AnilistClient anilistClient;

    @Inject
    AnilistMapper anilistMapper;

    @Inject
    SettingService settingService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public AnilistMediaSummaryPage search(String query, Integer page, Integer size, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey(query, String.valueOf(page), String.valueOf(size));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getAnilistCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, AnilistMediaSummaryPage.class, objectMapper),
                () -> fetchSearchFromAnilist(query, page, size),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public AnilistMediaResponse findById(Integer anilistId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("media", String.valueOf(anilistId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getAnilistCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, AnilistMediaResponse.class, objectMapper),
                () -> fetchMediaFromAnilist(anilistId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
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
                .data(anilistMapper.toSummaryList(page1Based.media()));
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
            if (HttpErrorUtils.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar um anime com o id informado na AniList");
            }
            Log.errorf(e, "Busca na AniList falhou para o id %d", anilistId);
            throw new IntegrationException("Busca na AniList falhou", e);
        }

        return response.data().media();
    }

}
