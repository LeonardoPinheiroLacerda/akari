package io.github.leonardopinheirolacerda.akari.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResultPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResultPage;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.TmdbClient;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbMovieDetailsResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbMovieSearchResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbSeasonResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbTranslationsResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbTvDetailsResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbTvSearchResponse;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.mapper.TmdbMapper;
import io.github.leonardopinheirolacerda.akari.model.IntegrationCache;
import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
import io.github.leonardopinheirolacerda.akari.utils.HttpErrors;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class TmdbSearchService {

    private static final int PAGE_SIZE = 20;

    @Inject
    @RestClient
    TmdbClient tmdbClient;

    @Inject
    TmdbMapper mapper;

    @Inject
    SettingService settingService;

    @Inject
    ObjectMapper objectMapper;

    @Transactional
    public TmdbMovieSearchResultPage searchMovies(String query, Integer page, String language, Boolean forceRefresh) {
        final boolean includeAdult = settingService.isTmdbIncludeAdult();
        final String effectiveLanguage = effectiveLanguage(language);
        final String cacheKey = CacheUtils.buildKey("movie", query, String.valueOf(page), effectiveLanguage, String.valueOf(includeAdult));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbMovieSearchResultPage.class, objectMapper),
                () -> fetchMovieSearch(query, page, effectiveLanguage, includeAdult),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbTvSearchResultPage searchTv(String query, Integer page, String language, Boolean forceRefresh) {
        final boolean includeAdult = settingService.isTmdbIncludeAdult();
        final String effectiveLanguage = effectiveLanguage(language);
        final String cacheKey = CacheUtils.buildKey("tv", query, String.valueOf(page), effectiveLanguage, String.valueOf(includeAdult));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbTvSearchResultPage.class, objectMapper),
                () -> fetchTvSearch(query, page, effectiveLanguage, includeAdult),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbMovieDetailsResponse getMovieDetails(Integer movieId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("movie", String.valueOf(movieId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbMovieDetailsResponse.class, objectMapper),
                () -> fetchMovieDetails(movieId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbImagesResponse getMovieImages(Integer movieId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("movie", "images", String.valueOf(movieId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbImagesResponse.class, objectMapper),
                () -> fetchMovieImages(movieId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbTranslationsResponse getMovieTranslations(Integer movieId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("movie", "translations", String.valueOf(movieId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbTranslationsResponse.class, objectMapper),
                () -> fetchMovieTranslations(movieId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbTvDetailsResponse getTvDetails(Integer seriesId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("tv", String.valueOf(seriesId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbTvDetailsResponse.class, objectMapper),
                () -> fetchTvDetails(seriesId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbImagesResponse getTvImages(Integer seriesId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("tv", "images", String.valueOf(seriesId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbImagesResponse.class, objectMapper),
                () -> fetchTvImages(seriesId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbTranslationsResponse getTvTranslations(Integer seriesId, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("tv", "translations", String.valueOf(seriesId));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbTranslationsResponse.class, objectMapper),
                () -> fetchTvTranslations(seriesId),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    @Transactional
    public TmdbSeasonResponse getSeason(Integer seriesId, Integer seasonNumber, Boolean forceRefresh) {
        final String cacheKey = CacheUtils.buildKey("tv", "season", String.valueOf(seriesId), String.valueOf(seasonNumber));

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbSeasonResponse.class, objectMapper),
                () -> fetchSeason(seriesId, seasonNumber),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    private TmdbMovieSearchResultPage fetchMovieSearch(String query, Integer page, String language, boolean includeAdult) {
        final TmdbMovieSearchResponse response;

        try {
            response = tmdbClient.searchMovie(query, page + 1, language, includeAdult);
        } catch (ProcessingException | WebApplicationException e) {
            Log.errorf(e, "Busca no TMDB falhou para \"%s\"", query);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }

        return new TmdbMovieSearchResultPage()
                .page(page)
                .size(PAGE_SIZE)
                .totalElements(response.totalResults().longValue())
                .totalPages(response.totalPages())
                .data(mapper.toMovieResultList(response.results()));
    }

    private TmdbTvSearchResultPage fetchTvSearch(String query, Integer page, String language, boolean includeAdult) {
        final TmdbTvSearchResponse response;

        try {
            response = tmdbClient.searchTv(query, page + 1, language, includeAdult);
        } catch (ProcessingException | WebApplicationException e) {
            Log.errorf(e, "Busca no TMDB falhou para \"%s\"", query);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }

        return new TmdbTvSearchResultPage()
                .page(page)
                .size(PAGE_SIZE)
                .totalElements(response.totalResults().longValue())
                .totalPages(response.totalPages())
                .data(mapper.toTvResultList(response.results()));
    }

    private TmdbMovieDetailsResponse fetchMovieDetails(Integer movieId) {
        try {
            return tmdbClient.getMovieDetails(movieId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar um filme com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de detalhes no TMDB falhou para o filme %d", movieId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbImagesResponse fetchMovieImages(Integer movieId) {
        try {
            return tmdbClient.getMovieImages(movieId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar um filme com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de imagens no TMDB falhou para o filme %d", movieId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbTranslationsResponse fetchMovieTranslations(Integer movieId) {
        try {
            return tmdbClient.getMovieTranslations(movieId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar um filme com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de traduções no TMDB falhou para o filme %d", movieId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbTvDetailsResponse fetchTvDetails(Integer seriesId) {
        try {
            return tmdbClient.getTvDetails(seriesId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar uma série com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de detalhes no TMDB falhou para a série %d", seriesId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbImagesResponse fetchTvImages(Integer seriesId) {
        try {
            return tmdbClient.getTvImages(seriesId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar uma série com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de imagens no TMDB falhou para a série %d", seriesId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbTranslationsResponse fetchTvTranslations(Integer seriesId) {
        try {
            return tmdbClient.getTvTranslations(seriesId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar uma série com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de traduções no TMDB falhou para a série %d", seriesId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private TmdbSeasonResponse fetchSeason(Integer seriesId, Integer seasonNumber) {
        try {
            return tmdbClient.getSeason(seriesId, seasonNumber);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrors.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar a temporada informada no TMDB");
            }
            Log.errorf(e, "Busca da temporada %d no TMDB falhou para a série %d", seasonNumber, seriesId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    private String effectiveLanguage(String requested) {
        if (requested != null && !requested.isBlank()) {
            return requested;
        }
        return settingService.getTmdbDefaultLanguage();
    }

}
