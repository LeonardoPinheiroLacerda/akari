package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbImageOption;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbLocalizedOption;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverridesSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImageResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationDataResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationEntryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbSearchService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Binding TMDB (série ou filme) e os overrides manuais de título, sinopse e imagens.
 */
@ApplicationScoped
public class AnimeTmdbService {

    @Inject
    AnimeTmdbMapper animeTmdbMapper;

    @Inject
    AnimeService animeService;

    @Inject
    TmdbSearchService tmdbSearchService;

    /**
     * Cria o binding TMDB do anime, confirmando antes que o {@code tmdbId} existe no TMDB pro
     * {@code mediaType} informado.
     *
     * @param anilistId id do anime na AniList
     * @param tmdbBindingRequest payload com {@code tmdbId} e {@code mediaType}
     * @return o estado TMDB recém-criado
     * @throws ResourceNotFoundException se o anime não existir, ou o {@code tmdbId} não existir
     *                                     no TMDB pro {@code mediaType} informado
     * @throws BusinessRuleException se o anime já tiver binding TMDB
     */
    @Transactional
    public TmdbAnimeStateResponse bindTmdb(Integer anilistId, TmdbBindingRequest tmdbBindingRequest) {
        Log.infof(
                "Vinculando anime %d ao TMDB %d (%s)",
                anilistId,
                tmdbBindingRequest.getTmdbId(),
                tmdbBindingRequest.getMediaType());

        final Anime anime = animeService.findOrThrow(anilistId);

        if (anime.tmdbId != null) {
            Log.warnf("Anime %d já tem binding TMDB", anilistId);
            throw new BusinessRuleException("Esse anime já tem binding TMDB");
        }

        final TmdbMediaType mediaType = tmdbBindingRequest.getMediaType();
        final Integer tmdbId = tmdbBindingRequest.getTmdbId();

        // Valida existência de ID no tmdb
        if (mediaType == TmdbMediaType.TV) {
            tmdbSearchService.getTvDetails(tmdbId, false);
        } else {
            tmdbSearchService.getMovieDetails(tmdbId, false);
        }

        anime.tmdbId = tmdbId;
        anime.tmdbMediaType = mediaType;
        anime.tmdbBoundAt = OffsetDateTime.now();

        Log.infof("Anime %d vinculado ao TMDB %d com sucesso", anilistId, tmdbId);

        return animeTmdbMapper.toState(anime);
    }

    /**
     * Remove o binding TMDB do anime; os overrides caem junto.
     *
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    @Transactional
    public void clearTmdbBinding(Integer anilistId) {
        Log.infof("Removendo binding TMDB do anime %d", anilistId);

        final Anime anime = findBoundAnimeOrThrow(anilistId);

        anime.tmdbId = null;
        anime.tmdbMediaType = null;
        anime.tmdbBoundAt = null;

        anime.overrideTitle = null;
        anime.overrideSynopsis = null;
        anime.overridePosterPath = null;
        anime.overrideBackdropPath = null;
        anime.overrideLogoPath = null;

        Log.infof("Binding TMDB do anime %d removido com sucesso", anilistId);
    }

    /**
     * Lê o estado TMDB atual do anime (binding + overrides).
     *
     * @param anilistId id do anime na AniList
     * @return o estado TMDB
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    public TmdbAnimeStateResponse getTmdbState(Integer anilistId) {
        final Anime anime = findBoundAnimeOrThrow(anilistId);
        return animeTmdbMapper.toState(anime);
    }

    /**
     * Substitui o bloco inteiro de overrides. Body com todos os campos {@code null} apaga a
     * linha de overrides.
     *
     * @param anilistId id do anime na AniList
     * @param tmdbOverrides novo estado de overrides
     * @return o estado TMDB atualizado
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    @Transactional
    public TmdbAnimeStateResponse updateTmdbOverrides(Integer anilistId, TmdbOverrides tmdbOverrides) {
        Log.infof("Atualizando overrides TMDB do anime %d", anilistId);

        final Anime anime = findBoundAnimeOrThrow(anilistId);

        anime.overrideTitle = tmdbOverrides.getTitle();
        anime.overrideSynopsis = tmdbOverrides.getSynopsis();
        anime.overridePosterPath = tmdbOverrides.getPosterPath();
        anime.overrideBackdropPath = tmdbOverrides.getBackdropPath();
        anime.overrideLogoPath = tmdbOverrides.getLogoPath();

        return animeTmdbMapper.toState(anime);
    }

    /**
     * Opções concretas por campo que aceita override: traduções de título/sinopse e imagens
     * (poster/backdrop/logo) disponíveis no TMDB pro binding do anime.
     *
     * @param anilistId id do anime na AniList
     * @param forceRefresh se {@code true}, ignora o cache local e re-busca do TMDB
     * @return o schema com as opções por campo (listas podem ser vazias)
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    public TmdbOverridesSchema getTmdbOverridesSchema(Integer anilistId, Boolean forceRefresh) {
        final Anime anime = findBoundAnimeOrThrow(anilistId);

        final boolean isTv = anime.tmdbMediaType == TmdbMediaType.TV;
        final Integer tmdbId = anime.tmdbId;

        final String defaultTitle;
        final String defaultOverview;
        final String defaultLanguage;

        if (isTv) {
            final TmdbTvDetailsResponse tv = tmdbSearchService.getTvDetails(tmdbId, forceRefresh);
            defaultTitle = tv.name();
            defaultOverview = tv.overview();
            defaultLanguage = tv.originalLanguage();
        } else {
            final TmdbMovieDetailsResponse movie = tmdbSearchService.getMovieDetails(tmdbId, forceRefresh);
            defaultTitle = movie.title();
            defaultOverview = movie.overview();
            defaultLanguage = movie.originalLanguage();
        }

        final List<TmdbTranslationEntryResponse> translations = isTv
                ? tmdbSearchService.getTvTranslations(tmdbId, forceRefresh).translations()
                : tmdbSearchService.getMovieTranslations(tmdbId, forceRefresh).translations();

        final TmdbImagesResponse images = isTv
                ? tmdbSearchService.getTvImages(tmdbId, forceRefresh)
                : tmdbSearchService.getMovieImages(tmdbId, forceRefresh);

        return new TmdbOverridesSchema()
                .title(collectTexts(defaultTitle, defaultLanguage, translations, TmdbTranslationDataResponse::localizedName))
                .synopsis(collectTexts(defaultOverview, defaultLanguage, translations, TmdbTranslationDataResponse::overview))
                .posterPath(toImageOptions(images.posters(), TmdbImageUtils::posterMedium))
                .backdropPath(toImageOptions(images.backdrops(), TmdbImageUtils::backdrop))
                .logoPath(toImageOptions(images.logos(), TmdbImageUtils::logo));
    }

    /**
     * Busca o anime e confirma que ele tem binding TMDB, ou lança 404. Usado também pelo
     * {@link AnimeTmdbFilePairingService}, que exige o mesmo binding pra existir.
     *
     * @param anilistId id do anime na AniList
     * @return o anime, com {@code tmdbId} garantidamente preenchido
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    public Anime findBoundAnimeOrThrow(Integer anilistId) {
        final Anime anime = animeService.findOrThrow(anilistId);

        if (anime.tmdbId == null) {
            Log.warnf("Anime %d não tem binding TMDB", anilistId);
            throw new ResourceNotFoundException("Não foi possível localizar um binding TMDB para o anime informado");
        }

        return anime;
    }

    private static List<TmdbLocalizedOption> collectTexts(
            String defaultValue,
            String defaultLanguage,
            List<TmdbTranslationEntryResponse> translations,
            Function<TmdbTranslationDataResponse, String> valueExtractor) {

        final List<TmdbLocalizedOption> result = new ArrayList<>();

        for (TmdbTranslationEntryResponse entry : translations) {
            if (entry.data() == null) {
                continue;
            }

            final String value = valueExtractor.apply(entry.data());

            if (value != null && !value.isBlank()) {
                final TmdbLocalizedOption text = new TmdbLocalizedOption()
                        .value(value)
                        .language(entry.iso6391());

                result.add(text);
            }
        }

        if (defaultValue != null && !defaultValue.isBlank()) {
            final TmdbLocalizedOption text = new TmdbLocalizedOption()
                    .value(defaultValue)
                    .language(defaultLanguage);

            result.add(text);
        }

        return result;
    }

    private static List<TmdbImageOption> toImageOptions(List<TmdbImageResponse> images, Function<String, String> urlBuilder) {
        if (images == null) {
            return List.of();
        }

        return images.stream()
                .map(image -> new TmdbImageOption()
                        .value(image.filePath())
                        .previewUrl(urlBuilder.apply(image.filePath()))
                        .language(image.iso6391())
                        .width(image.width())
                        .height(image.height()))
                .toList();
    }

}
