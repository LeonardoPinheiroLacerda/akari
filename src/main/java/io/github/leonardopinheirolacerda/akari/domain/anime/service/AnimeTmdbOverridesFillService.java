package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbOverridesFill;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImageResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationDataResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationEntryResponse;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Preenchimento automático dos overrides TMDB do anime (título, sinopse, backdrop e logo) a
 * partir das traduções e imagens do título vinculado, pelos idiomas configurados em
 * {@code setting} — usado pelo step {@code TMDB_OVERRIDES} da ingestão.
 */
@ApplicationScoped
public class AnimeTmdbOverridesFillService {

    // Valor da lista de idiomas do logo que representa o logo sem idioma.
    private static final String NO_LANGUAGE = "none";

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    SettingService settingService;

    /**
     * Preenche título, sinopse, backdrop e logo do anime a partir das traduções e imagens do
     * TMDB (cache-first).
     *
     * <p>Cada idioma é procurado do mais específico ao mais amplo: idioma e país
     * ({@code pt-BR}), depois só o idioma ({@code pt}, de qualquer país) — primeiro o idioma
     * configurado, depois o de fallback ({@code tmdb.override.fallback-language}).
     *
     * <p>Título e sinopse: setting de idioma ({@code tmdb.override.title-language}/
     * {@code tmdb.override.synopsis-language}) vazia grava {@code null}, mantendo o dado da
     * AniList; preenchida, usa a primeira tradução com texto nessa ordem. Backdrop: só preenche
     * se o anime ainda não tem — o mais votado sem idioma, ou o mais votado na ordem do idioma
     * de fallback. O que não é encontrado nem no idioma configurado nem no de fallback é gravado
     * {@code null}.
     *
     * <p>Logo: usa só {@code tmdb.image.logo-language}, uma lista de idiomas separados por
     * vírgula em ordem de preferência ({@code en-US,ja,none}), sem o idioma de fallback — o
     * mais votado do primeiro idioma da lista que tiver logo, com {@code none} pro logo sem
     * idioma. Lista vazia grava {@code null}.
     *
     * <p>Traduções e imagens são buscadas antes, fora de transação, e a gravação roda numa
     * transação curta ({@link #writeOverrides}) — nenhuma transação fica aberta durante as
     * chamadas ao TMDB.
     *
     * @param anilistId id do anime na AniList
     * @return os overrides gravados e os campos sem opção no TMDB
     * @throws ResourceNotFoundException se o anime não existir, não tiver binding TMDB ou o
     *                                     {@code tmdbId} não existir mais no TMDB
     * @throws IntegrationException se a busca no TMDB falhar
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e os
     *                                           dados não estiverem em cache
     * @throws BusinessRuleException se alguma preferência de idioma tiver um idioma inválido
     */
    public TmdbOverridesFill fillOverrides(Integer anilistId) {
        Log.infof("Preenchendo overrides TMDB do anime %d", anilistId);

        final Anime boundAnime = animeTmdbService.findBoundAnimeOrThrow(anilistId);

        final List<TmdbTranslationEntryResponse> translations = animeTmdbService.fetchTranslations(
                boundAnime.tmdbId,
                boundAnime.tmdbMediaType,
                false
        );
        final TmdbImagesResponse images = animeTmdbService.fetchImages(
                boundAnime.tmdbId,
                boundAnime.tmdbMediaType,
                false
        );

        return writeOverrides(anilistId, translations, images);
    }

    /**
     * Escolhe e grava os overrides do anime a partir das traduções e imagens já buscadas, numa
     * transação curta. Busca o anime de novo pra gravar na entidade gerenciada por esta
     * transação. Package-private (não {@code private}) pra que o {@code @Transactional} seja
     * interceptado quando chamado por {@link #fillOverrides}.
     *
     * @param anilistId id do anime na AniList
     * @param translations traduções do título no TMDB
     * @param images imagens do título no TMDB
     * @return os overrides gravados e os campos sem opção no TMDB
     */
    @Transactional
    TmdbOverridesFill writeOverrides(Integer anilistId,
                                     List<TmdbTranslationEntryResponse> translations,
                                     TmdbImagesResponse images) {

        final Anime anime = animeTmdbService.findBoundAnimeOrThrow(anilistId);

        final Locale titleLanguage = settingService.getTmdbOverrideTitleLanguage();
        final Locale synopsisLanguage = settingService.getTmdbOverrideSynopsisLanguage();
        final List<String> logoLanguages = settingService.getTmdbImageLogoLanguages();
        final Locale fallbackLanguage = settingService.getTmdbOverrideFallbackLanguage();

        final List<String> notFound = new ArrayList<>();

        anime.overrideTitle = selectText(
                translations,
                titleLanguage,
                fallbackLanguage,
                TmdbTranslationDataResponse::localizedName
        );

        if (titleLanguage != null && anime.overrideTitle == null) {
            notFound.add("title");
        }

        anime.overrideSynopsis = selectText(
                translations,
                synopsisLanguage,
                fallbackLanguage,
                TmdbTranslationDataResponse::overview
        );

        if (synopsisLanguage != null && anime.overrideSynopsis == null) {
            notFound.add("synopsis");
        }

        if (anime.overrideBackdropPath == null) {
            anime.overrideBackdropPath = selectBackdrop(images.backdrops(), fallbackLanguage);

            if (anime.overrideBackdropPath == null) {
                notFound.add("backdropPath");
            }
        }

        anime.overrideLogoPath = selectLogo(images.logos(), logoLanguages);

        if (!logoLanguages.isEmpty() && anime.overrideLogoPath == null) {
            notFound.add("logoPath");
        }

        Log.infof("Overrides TMDB do anime %d preenchidos (sem opção: %s)", anilistId, notFound);

        return new TmdbOverridesFill(
                anime.overrideTitle,
                anime.overrideSynopsis,
                anime.overrideBackdropPath,
                anime.overrideLogoPath,
                notFound
        );
    }

    /**
     * Primeiro texto de tradução na ordem de idiomas de {@link #languageChain}, a partir do
     * idioma configurado e depois do de fallback. Tradução sem texto conta como inexistente.
     *
     * @param translations traduções do título no TMDB
     * @param language idioma configurado ({@code pt-BR} ou {@code pt}), ou {@code null} pra não
     *                 escolher nenhuma
     * @param fallbackLanguage idioma de fallback, ou {@code null} pra não ter fallback
     * @param valueExtractor campo da tradução (título ou sinopse)
     * @return o texto escolhido, ou {@code null} se o idioma for {@code null} ou nada for
     *         encontrado
     */
    private static String selectText(List<TmdbTranslationEntryResponse> translations,
                                     Locale language,
                                     Locale fallbackLanguage,
                                     Function<TmdbTranslationDataResponse, String> valueExtractor) {

        if (language == null) {
            return null;
        }

        for (Locale locale : languageChain(language, fallbackLanguage)) {
            final String text = findText(translations, locale, valueExtractor);

            if (text != null) {
                return text;
            }
        }

        return null;
    }

    private static String findText(List<TmdbTranslationEntryResponse> translations,
                                   Locale locale,
                                   Function<TmdbTranslationDataResponse, String> valueExtractor) {

        return translations.stream()
                .filter(entry -> matches(locale, entry.iso6391(), entry.iso31661()))
                .map(TmdbTranslationEntryResponse::data)
                .filter(Objects::nonNull)
                .map(valueExtractor)
                .filter(value -> value != null && !value.isBlank())
                .findFirst()
                .orElse(null);
    }

    /**
     * Backdrop mais votado sem idioma; sem nenhum, o mais votado na ordem do idioma de
     * fallback.
     *
     * @param backdrops backdrops do título no TMDB
     * @param fallbackLanguage idioma de fallback, ou {@code null} pra não ter fallback
     * @return o {@code filePath} escolhido, ou {@code null} se nada for encontrado
     */
    private static String selectBackdrop(List<TmdbImageResponse> backdrops,
                                         Locale fallbackLanguage) {

        final String backdrop = selectMostVotedImageWithoutLanguage(backdrops);

        if (backdrop != null) {
            return backdrop;
        }

        return selectImageByLanguage(backdrops, languageChain(fallbackLanguage));
    }

    /**
     * Logo mais votado do primeiro idioma da lista que tiver logo. Cada idioma da lista é
     * procurado do mais específico ao mais amplo ({@link #languageChain}); {@code none} procura
     * o logo sem idioma.
     *
     * @param logos logos do título no TMDB
     * @param languages idiomas em ordem de preferência ({@code en-US}, {@code ja},
     *                  {@code none}); vazia pra não escolher nenhum
     * @return o {@code filePath} escolhido, ou {@code null} se nada for encontrado
     */
    private static String selectLogo(List<TmdbImageResponse> logos, List<String> languages) {
        for (String language : languages) {
            final String logo = selectLogoByLanguage(logos, language);

            if (logo != null) {
                return logo;
            }
        }

        return null;
    }

    private static String selectLogoByLanguage(List<TmdbImageResponse> logos, String language) {
        if (NO_LANGUAGE.equalsIgnoreCase(language)) {
            return selectMostVotedImageWithoutLanguage(logos);
        }

        return selectImageByLanguage(logos, languageChain(Locale.forLanguageTag(language)));
    }

    /**
     * Imagem mais votada no primeiro idioma da cadeia que tiver alguma imagem.
     *
     * @param images galeria de imagens do TMDB
     * @param chain idiomas em ordem de preferência
     * @return o {@code filePath} escolhido, ou {@code null} se nenhum idioma tiver imagem
     */
    private static String selectImageByLanguage(List<TmdbImageResponse> images,
                                                List<Locale> chain) {

        for (Locale locale : chain) {
            final String image = selectMostVotedImage(
                    images,
                    candidate -> matches(locale, candidate.iso6391(), candidate.iso31661())
            );

            if (image != null) {
                return image;
            }
        }

        return null;
    }

    private static String selectMostVotedImageWithoutLanguage(List<TmdbImageResponse> images) {
        return selectMostVotedImage(images, image -> image.iso6391() == null);
    }

    /**
     * Imagem mais popular (maior {@code voteCount}, desempate por {@code voteAverage}, depois
     * por largura) entre as que atendem ao critério.
     *
     * @param images galeria de imagens do TMDB
     * @param criteria critério de idioma da imagem
     * @return o {@code filePath} escolhido, ou {@code null} se nenhuma imagem atender
     */
    private static String selectMostVotedImage(List<TmdbImageResponse> images,
                                               Predicate<TmdbImageResponse> criteria) {

        if (images == null) {
            return null;
        }

        return images.stream()
                .filter(criteria)
                .max(Comparator
                        .comparing(TmdbImageResponse::voteCount, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TmdbImageResponse::voteAverage, Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(TmdbImageResponse::width, Comparator.nullsFirst(Comparator.naturalOrder())))
                .map(TmdbImageResponse::filePath)
                .orElse(null);
    }

    /**
     * Ordem de procura dos idiomas, do mais específico ao mais amplo: pra cada idioma, na ordem
     * recebida, idioma e país ({@code pt-BR}) quando ele tem país, e depois só o idioma
     * ({@code pt}). Idiomas {@code null} são ignorados.
     *
     * @param languages idiomas em ordem de preferência
     * @return os idiomas a procurar, sem repetição
     */
    private static List<Locale> languageChain(Locale... languages) {
        final List<Locale> chain = new ArrayList<>();

        for (Locale locale : languages) {
            if (locale == null) {
                continue;
            }

            chain.add(locale);

            if (!locale.getCountry().isEmpty()) {
                chain.add(Locale.of(locale.getLanguage()));
            }
        }

        return chain.stream()
                .distinct()
                .toList();
    }

    /**
     * Confere se o idioma e o país de uma tradução ou imagem do TMDB atendem ao idioma
     * procurado — sem país no idioma procurado, qualquer país serve.
     */
    private static boolean matches(Locale locale, String language, String country) {
        if (!locale.getLanguage().equalsIgnoreCase(language)) {
            return false;
        }

        return locale.getCountry().isEmpty() || locale.getCountry().equalsIgnoreCase(country);
    }
}
