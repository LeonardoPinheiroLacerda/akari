package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbImageOption;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbLocalizedOption;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverridesSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImageResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationDataResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationEntryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
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
    TmdbService tmdbService;

    @Inject
    SettingService settingService;

    /**
     * Cria o binding TMDB do anime, confirmando antes que o {@code tmdbId} existe no TMDB pro
     * {@code mediaType} informado. Não mexe nos overrides de imagem.
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

        // A ficha também valida a existência do id no TMDB (404 se não existir) — lida uma vez só,
        // antes de gravar, e reaproveitada na resposta.
        final TmdbBindingDetails details = fetchDetails(tmdbId, mediaType);

        anime.tmdbId = tmdbId;
        anime.tmdbMediaType = mediaType;
        anime.tmdbBoundAt = OffsetDateTime.now();

        Log.infof("Anime %d vinculado ao TMDB %d com sucesso", anilistId, tmdbId);

        return animeTmdbMapper.toState(anime, details);
    }

    /**
     * Remove o binding TMDB do anime, junto com os overrides e os pairings TMDB dos arquivos da
     * pasta — os pairings apontam pra temporadas/episódios do título desvinculado e ficariam
     * errados num novo binding.
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

        final long deletedPairings = TmdbFilePairing.deleteByFolderId(anime.folder.id);

        Log.infof("Binding TMDB do anime %d removido com sucesso (%d pairings apagados)", anilistId, deletedPairings);
    }

    /**
     * Lê o estado TMDB atual do anime (binding + overrides + ficha do título vinculado).
     *
     * @param anilistId id do anime na AniList
     * @return o estado TMDB
     * @throws ResourceNotFoundException se o anime não existir, não tiver binding TMDB ou o
     *                                     {@code tmdbId} não existir mais no TMDB
     * @throws IntegrationException se a busca da ficha no TMDB falhar
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a ficha
     *                                           não estiver em cache
     */
    public TmdbAnimeStateResponse getTmdbState(Integer anilistId) {
        final Anime anime = findBoundAnimeOrThrow(anilistId);
        return animeTmdbMapper.toState(anime, fetchDetails(anime.tmdbId, anime.tmdbMediaType));
    }

    /**
     * Substitui o bloco inteiro de overrides. Body com todos os campos {@code null} apaga a
     * linha de overrides.
     *
     * @param anilistId id do anime na AniList
     * @param tmdbOverrides novo estado de overrides
     * @return o estado TMDB atualizado
     * @throws ResourceNotFoundException se o anime não existir, não tiver binding TMDB ou o
     *                                     {@code tmdbId} não existir mais no TMDB
     * @throws IntegrationException se a busca da ficha no TMDB falhar (desfaz a gravação)
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a ficha
     *                                           não estiver em cache (desfaz a gravação)
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

        return animeTmdbMapper.toState(anime, fetchDetails(anime.tmdbId, anime.tmdbMediaType));
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

        final Integer tmdbId = anime.tmdbId;

        final TmdbBindingDetails details = fetchDetails(tmdbId, anime.tmdbMediaType, forceRefresh);

        final List<TmdbTranslationEntryResponse> translations = anime.tmdbMediaType == TmdbMediaType.TV
                ? tmdbService.getTvTranslations(tmdbId, forceRefresh).translations()
                : tmdbService.getMovieTranslations(tmdbId, forceRefresh).translations();

        final TmdbImagesResponse images = fetchImages(tmdbId, anime.tmdbMediaType, forceRefresh);

        return new TmdbOverridesSchema()
                .title(collectTexts(
                        details.getTitle(),
                        details.getOriginalLanguage(),
                        translations,
                        TmdbTranslationDataResponse::localizedName
                ))
                .synopsis(collectTexts(
                        details.getOverview(),
                        details.getOriginalLanguage(),
                        translations,
                        TmdbTranslationDataResponse::overview
                ))
                .posterPath(toImageOptions(images.posters(), TmdbImageUtils::posterMedium))
                .backdropPath(toImageOptions(images.backdrops(), TmdbImageUtils::backdrop))
                .logoPath(toImageOptions(images.logos(), TmdbImageUtils::logo));
    }

    /**
     * Busca o anime e confirma que ele tem binding TMDB de série — pairing de episódios só existe
     * pra série. Usado pelo {@link AnimeTmdbFilePairingService} e pelo
     * {@link AnimeTmdbProposalService}.
     *
     * @param anilistId id do anime na AniList
     * @return o anime, com binding TMDB de série
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     * @throws BusinessRuleException se o binding TMDB for de filme
     */
    public Anime findTvBoundAnimeOrThrow(Integer anilistId) {
        final Anime anime = findBoundAnimeOrThrow(anilistId);

        if (anime.tmdbMediaType != TmdbMediaType.TV) {
            Log.warnf("Anime %d tem binding TMDB de filme, sem pairing de episódios", anilistId);
            throw new BusinessRuleException("Pairing de episódios só existe pra binding TMDB de série");
        }

        return anime;
    }

    /**
     * Lê a ficha da série ou do filme no TMDB (cache-first) e converte pro resumo do contrato.
     *
     * <p>Público pra {@code AnimeTmdbProposalService} montar a mesma ficha na proposta de
     * binding, sem duplicar a escolha entre série e filme, e pro step {@code TMDB_BIND} da
     * ingestão conferir se o título existe no TMDB antes do bind.
     *
     * @param tmdbId id da série/filme no TMDB
     * @param mediaType se o binding é série ({@code TV}) ou filme ({@code MOVIE})
     * @return a ficha resumida, sem overrides aplicados
     * @throws ResourceNotFoundException se o {@code tmdbId} não existir mais no TMDB
     * @throws IntegrationException se a chamada ao TMDB falhar
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a ficha
     *                                           não estiver em cache
     */
    public TmdbBindingDetails fetchDetails(Integer tmdbId, TmdbMediaType mediaType) {
        return fetchDetails(tmdbId, mediaType, false);
    }

    private TmdbBindingDetails fetchDetails(Integer tmdbId, TmdbMediaType mediaType, Boolean forceRefresh) {
        if (mediaType == TmdbMediaType.TV) {
            return animeTmdbMapper.toDetails(tmdbService.getTvDetails(tmdbId, forceRefresh));
        }

        return animeTmdbMapper.toDetails(tmdbService.getMovieDetails(tmdbId, forceRefresh));
    }

    /**
     * Busca o anime e confirma que ele tem binding TMDB, ou lança 404 — usado pelas operações
     * sobre um binding existente e por {@link #findTvBoundAnimeOrThrow}.
     *
     * @param anilistId id do anime na AniList
     * @return o anime, com {@code tmdbId} garantidamente preenchido
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     */
    private Anime findBoundAnimeOrThrow(Integer anilistId) {
        final Anime anime = animeService.findOrThrow(anilistId);

        if (anime.tmdbId == null) {
            Log.warnf("Anime %d não tem binding TMDB", anilistId);
            throw new ResourceNotFoundException("Não foi possível localizar um binding TMDB para o anime informado");
        }

        return anime;
    }

    private TmdbImagesResponse fetchImages(Integer tmdbId, TmdbMediaType mediaType, Boolean forceRefresh) {
        if (mediaType == TmdbMediaType.TV) {
            return tmdbService.getTvImages(tmdbId, forceRefresh);
        }

        return tmdbService.getMovieImages(tmdbId, forceRefresh);
    }

    /**
     * Escolhe a imagem mais popular (maior {@code voteCount}, desempate por {@code voteAverage},
     * depois por largura) dentro do idioma preferido; sem nenhuma imagem nesse idioma, cai pra
     * popularidade entre todas — nem todo título tem logo japonês ou backdrop sem idioma
     * catalogado no TMDB.
     *
     * @param images galeria de imagens do TMDB (backdrops ou logos)
     * @param preferredLanguage {@code iso_639_1} preferido, ou {@code null} pra imagem sem idioma
     * @return o {@code filePath} escolhido, ou {@code null} se a galeria estiver vazia
     */
    private static String selectImage(List<TmdbImageResponse> images, String preferredLanguage) {
        if (images == null || images.isEmpty()) {
            return null;
        }

        final List<TmdbImageResponse> filtered = images.stream()
                .filter(image -> Objects.equals(image.iso6391(), preferredLanguage))
                .toList();

        final List<TmdbImageResponse> candidates = filtered.isEmpty()
                ? images
                : filtered;

        return candidates.stream()
                .max(Comparator
                        .comparing(TmdbImageResponse::voteCount, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TmdbImageResponse::voteAverage, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TmdbImageResponse::width, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(TmdbImageResponse::filePath)
                .orElse(null);
    }

    private static List<TmdbLocalizedOption> collectTexts(
            String defaultValue,
            String defaultLanguage,
            List<TmdbTranslationEntryResponse> translations,
            Function<TmdbTranslationDataResponse, String> valueExtractor) {

        final List<TmdbLocalizedOption> result = new ArrayList<>();

        for (TmdbTranslationEntryResponse entry : translations) {
            if (entry.data() != null) {
                addIfHasText(result, valueExtractor.apply(entry.data()), entry.iso6391());
            }
        }

        addIfHasText(result, defaultValue, defaultLanguage);

        return result;
    }

    private static void addIfHasText(List<TmdbLocalizedOption> options, String value, String language) {
        if (value == null || value.isBlank()) {
            return;
        }

        options.add(new TmdbLocalizedOption()
                .value(value)
                .language(language));
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
