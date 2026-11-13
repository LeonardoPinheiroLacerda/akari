package io.github.leonardopinheirolacerda.akari.domain.tmdb.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResultPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResultPage;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.IntegrationCache;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.TmdbClient;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieSearchResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvSearchResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.mapper.TmdbMapper;
import io.github.leonardopinheirolacerda.akari.utils.CacheUtils;
import io.github.leonardopinheirolacerda.akari.utils.HttpErrorUtils;
import io.github.leonardopinheirolacerda.akari.utils.TmdbLanguageUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.eclipse.microprofile.rest.client.inject.RestClient;

/**
 * Busca e detalhes de filme/série no TMDB, cache-first via {@link IntegrationCache} (TTL de
 * {@code tmdb.cache.ttl}).
 *
 * <p>Todo método público é {@code @Transactional} mesmo sendo leitura: o cache-aside
 * ({@link CacheUtils#resolve}) grava em {@link IntegrationCache} como efeito colateral de um
 * cache miss. {@code language} nulo/em branco cai pra {@code tmdb.default-language}; busca usa
 * {@code tmdb.include-adult} pra decidir se inclui conteúdo adulto.
 */
@ApplicationScoped
public class TmdbService {

    private static final int PAGE_SIZE = 20;

    @Inject
    @RestClient
    TmdbClient tmdbClient;

    @Inject
    TmdbMapper tmdbMapper;

    @Inject
    SettingService settingService;

    @Inject
    ObjectMapper objectMapper;

    /**
     * Busca paginada de filme no TMDB, cache-first.
     *
     * @param query termo de busca
     * @param page página desejada, 0-based
     * @param language idioma das traduções; {@code null}/em branco usa
     *                  {@code tmdb.default-language}
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return a página de resultados encontrados
     * @throws IntegrationException se a chamada ao TMDB falhar
     */
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

    /**
     * Busca paginada de série no TMDB, cache-first.
     *
     * @param query termo de busca
     * @param page página desejada, 0-based
     * @param language idioma das traduções; {@code null}/em branco usa
     *                  {@code tmdb.default-language}
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return a página de resultados encontrados
     * @throws IntegrationException se a chamada ao TMDB falhar
     */
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

    /**
     * Lê os detalhes de um filme no TMDB, cache-first.
     *
     * @param movieId id do filme no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return os detalhes do filme
     * @throws ResourceNotFoundException se o {@code movieId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê as imagens disponíveis (posters/backdrops) de um filme no TMDB, cache-first.
     *
     * @param movieId id do filme no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return as imagens do filme
     * @throws ResourceNotFoundException se o {@code movieId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê as traduções de título/sinopse disponíveis de um filme no TMDB, cache-first.
     *
     * @param movieId id do filme no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return as traduções do filme
     * @throws ResourceNotFoundException se o {@code movieId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê os detalhes de uma série no TMDB, cache-first.
     *
     * @param seriesId id da série no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return os detalhes da série
     * @throws ResourceNotFoundException se o {@code seriesId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê as imagens disponíveis (posters/backdrops) de uma série no TMDB, cache-first.
     *
     * @param seriesId id da série no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return as imagens da série
     * @throws ResourceNotFoundException se o {@code seriesId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê as traduções de título/sinopse disponíveis de uma série no TMDB, cache-first.
     *
     * @param seriesId id da série no TMDB
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return as traduções da série
     * @throws ResourceNotFoundException se o {@code seriesId} não existir no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
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

    /**
     * Lê os episódios de uma temporada de série no TMDB, cache-first, com nome e sinopse de cada
     * episódio nos idiomas das preferências {@code tmdb.episode.name-language} e
     * {@code tmdb.episode.synopsis-language}.
     *
     * <p>A temporada é buscada primeiro sem idioma (o padrão do TMDB), que dá a estrutura e o
     * texto final de quem não tem tradução. Pra cada episódio, nome e sinopse percorrem os
     * idiomas do mais específico ao mais amplo — idioma e país ({@code pt-BR}), só o idioma
     * ({@code pt}), e o mesmo pro {@code tmdb.override.fallback-language} —, buscando a
     * temporada naquele idioma só quando ainda falta algum episódio. Episódio com sinopse vazia
     * num idioma conta como sem tradução nele: o TMDB devolve um nome genérico
     * ("Episódio 5") e a sinopse vazia nesse caso. Preferência vazia mantém o texto do padrão
     * do TMDB.
     *
     * @param seriesId id da série no TMDB
     * @param seasonNumber número da temporada
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return a temporada com seus episódios
     * @throws ResourceNotFoundException se a série ou a temporada informada não existirem no
     *         TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     * @throws BusinessRuleException se alguma preferência de idioma tiver um idioma inválido
     */
    @Transactional
    public TmdbSeasonResponse getSeason(Integer seriesId, Integer seasonNumber, Boolean forceRefresh) {
        final TmdbSeasonResponse season = getSeasonInLanguage(
                seriesId,
                seasonNumber,
                null,
                forceRefresh
        );

        final Locale nameLanguage = settingService.getTmdbEpisodeNameLanguage();
        final Locale synopsisLanguage = settingService.getTmdbEpisodeSynopsisLanguage();
        final Locale fallbackLanguage = settingService.getTmdbOverrideFallbackLanguage();

        final List<Locale> nameLanguages = nameLanguage == null
                ? List.of()
                : TmdbLanguageUtils.languageChain(nameLanguage, fallbackLanguage);

        final List<Locale> synopsisLanguages = synopsisLanguage == null
                ? List.of()
                : TmdbLanguageUtils.languageChain(synopsisLanguage, fallbackLanguage);

        if (season.episodes() == null) {
            return season;
        }

        if (nameLanguages.isEmpty() && synopsisLanguages.isEmpty()) {
            return season;
        }

        final Map<Locale, Map<Integer, TmdbEpisodeResponse>> episodesByLanguage = new HashMap<>();

        final Function<Locale, Map<Integer, TmdbEpisodeResponse>> episodesIn = language -> episodesByLanguage
                .computeIfAbsent(
                        language,
                        key -> episodesByNumber(seriesId, seasonNumber, key, forceRefresh)
                );

        final List<TmdbEpisodeResponse> episodes = season.episodes()
                .stream()
                .map(episode -> withTexts(
                        episode,
                        localizedText(episode, nameLanguages, episodesIn, TmdbEpisodeResponse::name),
                        localizedText(episode, synopsisLanguages, episodesIn, TmdbEpisodeResponse::overview)
                ))
                .toList();

        return new TmdbSeasonResponse(
                season.objectId(),
                season.airDate(),
                episodes,
                season.name(),
                season.overview(),
                season.id(),
                season.networks(),
                season.posterPath(),
                season.seasonNumber(),
                season.voteAverage()
        );
    }

    /**
     * Lê só os episódios de uma temporada de série no TMDB, cache-first. Temporada que não
     * existe no TMDB conta como vazia — quem anda pela série (pairing de episódios) segue com o
     * resto dela.
     *
     * @param seriesId id da série no TMDB
     * @param seasonNumber número da temporada
     * @return os episódios da temporada; vazio se a temporada não existir ou não tiver episódios
     * @throws IntegrationException se a chamada ao TMDB falhar por outro motivo
     */
    @Transactional
    public List<TmdbEpisodeResponse> getSeasonEpisodes(Integer seriesId, Integer seasonNumber) {
        final TmdbSeasonResponse season;

        try {
            season = getSeason(seriesId, seasonNumber, false);
        } catch (ResourceNotFoundException e) {
            Log.warnf("Temporada %d não existe no TMDB %d, tratada como vazia", seasonNumber, seriesId);
            return List.of();
        }

        if (season.episodes() == null) {
            return List.of();
        }

        return season.episodes();
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
                .data(tmdbMapper.toMovieResultList(response.results()));
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
                .data(tmdbMapper.toTvResultList(response.results()));
    }

    private TmdbMovieDetailsResponse fetchMovieDetails(Integer movieId) {
        try {
            return tmdbClient.getMovieDetails(movieId);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrorUtils.isNotFound(e)) {
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
            if (HttpErrorUtils.isNotFound(e)) {
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
            if (HttpErrorUtils.isNotFound(e)) {
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
            if (HttpErrorUtils.isNotFound(e)) {
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
            if (HttpErrorUtils.isNotFound(e)) {
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
            if (HttpErrorUtils.isNotFound(e)) {
                throw new ResourceNotFoundException("Não foi possível localizar uma série com o id informado no TMDB");
            }
            Log.errorf(e, "Busca de traduções no TMDB falhou para a série %d", seriesId);
            throw new IntegrationException("Busca no TMDB falhou", e);
        }
    }

    /**
     * Temporada num idioma, cache-first. {@code language} {@code null} busca sem idioma (o
     * padrão do TMDB), com a mesma chave de cache de antes de existir o idioma.
     */
    private TmdbSeasonResponse getSeasonInLanguage(Integer seriesId,
                                                   Integer seasonNumber,
                                                   Locale language,
                                                   Boolean forceRefresh) {

        final String languageTag = language == null
                ? null
                : language.toLanguageTag();

        final String cacheKey = seasonCacheKey(seriesId, seasonNumber, languageTag);

        return CacheUtils.resolve(
                forceRefresh,
                settingService.getTmdbCacheTtl(),
                () -> IntegrationCache.readEntry(cacheKey, TmdbSeasonResponse.class, objectMapper),
                () -> fetchSeason(seriesId, seasonNumber, languageTag),
                payload -> IntegrationCache.writeEntry(cacheKey, payload, objectMapper)
        );
    }

    private static String seasonCacheKey(Integer seriesId, Integer seasonNumber, String languageTag) {
        if (languageTag == null) {
            return CacheUtils.buildKey("tv", "season", String.valueOf(seriesId), String.valueOf(seasonNumber));
        }

        return CacheUtils.buildKey(
                "tv",
                "season",
                String.valueOf(seriesId),
                String.valueOf(seasonNumber),
                languageTag
        );
    }

    /**
     * Episódios da temporada num idioma, pelo número do episódio.
     */
    private Map<Integer, TmdbEpisodeResponse> episodesByNumber(Integer seriesId,
                                                                Integer seasonNumber,
                                                                Locale language,
                                                                Boolean forceRefresh) {

        final TmdbSeasonResponse season = getSeasonInLanguage(seriesId, seasonNumber, language, forceRefresh);

        if (season.episodes() == null) {
            return Map.of();
        }

        return season.episodes()
                .stream()
                .filter(episode -> episode.episodeNumber() != null)
                .collect(Collectors.toMap(
                        TmdbEpisodeResponse::episodeNumber,
                        Function.identity(),
                        (first, second) -> first
                ));
    }

    /**
     * Texto do episódio no primeiro idioma em que ele tem tradução (sinopse não vazia); sem
     * nenhum, o texto do padrão do TMDB.
     */
    private static String localizedText(TmdbEpisodeResponse episode,
                                        List<Locale> languages,
                                        Function<Locale, Map<Integer, TmdbEpisodeResponse>> episodesIn,
                                        Function<TmdbEpisodeResponse, String> text) {

        for (Locale language : languages) {
            final TmdbEpisodeResponse translated = episodesIn
                    .apply(language)
                    .get(episode.episodeNumber());

            if (translated == null) {
                continue;
            }

            if (translated.overview() == null || translated.overview().isBlank()) {
                continue;
            }

            return text.apply(translated);
        }

        return text.apply(episode);
    }

    private static TmdbEpisodeResponse withTexts(TmdbEpisodeResponse episode, String name, String overview) {
        return new TmdbEpisodeResponse(
                episode.tmdbId(),
                episode.seasonNumber(),
                episode.episodeNumber(),
                episode.episodeType(),
                name,
                overview,
                episode.airDate(),
                episode.stillPath(),
                episode.runtime(),
                episode.productionCode(),
                episode.showId(),
                episode.voteAverage(),
                episode.voteCount(),
                episode.crew(),
                episode.guestStars()
        );
    }

    private TmdbSeasonResponse fetchSeason(Integer seriesId, Integer seasonNumber, String language) {
        try {
            return tmdbClient.getSeason(seriesId, seasonNumber, language);
        } catch (ProcessingException | WebApplicationException e) {
            if (HttpErrorUtils.isNotFound(e)) {
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
