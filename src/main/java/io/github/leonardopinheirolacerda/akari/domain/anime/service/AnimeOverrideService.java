package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Resolve, pra cada campo overridable de um anime, qual valor prevalece: o override manual do
 * TMDB (quando setado) ou o dado cru da AniList. Regra única, reaproveitada por qualquer
 * service que precise exibir anime (base, relations, ...), em vez de cada um decidir isso por
 * conta própria.
 */
@ApplicationScoped
public class AnimeOverrideService {

    /**
     * Título principal — só o {@code main} é afetado; english/japanese/synonyms continuam da
     * AniList mesmo com override.
     *
     * @param anime anime a resolver
     * @return o override, se setado; senão o título da AniList
     */
    public String resolveTitle(Anime anime) {
        return hasText(anime.overrideTitle)
                ? anime.overrideTitle
                : anime.titleMain;
    }

    /**
     * @param anime anime a resolver
     * @return o override, se setado; senão a sinopse da AniList
     */
    public String resolveSynopsis(Anime anime) {
        return hasText(anime.overrideSynopsis)
                ? anime.overrideSynopsis
                : anime.synopsis;
    }

    /**
     * @param anime anime a resolver
     * @return poster pequeno — do override (TMDB) se setado, senão da AniList
     */
    public String resolveThumbnailSmall(Anime anime) {
        return hasText(anime.overridePosterPath)
                ? TmdbImageUtils.posterSmall(anime.overridePosterPath)
                : anime.thumbnailSmall;
    }

    /**
     * @param anime anime a resolver
     * @return poster médio — do override (TMDB) se setado, senão da AniList
     */
    public String resolveThumbnailMedium(Anime anime) {
        return hasText(anime.overridePosterPath)
                ? TmdbImageUtils.posterMedium(anime.overridePosterPath)
                : anime.thumbnailMedium;
    }

    /**
     * @param anime anime a resolver
     * @return poster grande — do override (TMDB) se setado, senão da AniList
     */
    public String resolveThumbnailLarge(Anime anime) {
        return hasText(anime.overridePosterPath)
                ? TmdbImageUtils.posterLarge(anime.overridePosterPath)
                : anime.thumbnailLarge;
    }

    /**
     * Banner só existe via override manual — a AniList não tem esse campo.
     *
     * @param anime anime a resolver
     * @return a URL do banner, ou {@code null} sem override
     */
    public String resolveBanner(Anime anime) {
        return TmdbImageUtils.backdrop(anime.overrideBackdropPath);
    }

    /**
     * Logo só existe via override manual — a AniList não tem esse campo.
     *
     * @param anime anime a resolver
     * @return a URL do logo, ou {@code null} sem override
     */
    public String resolveLogo(Anime anime) {
        return TmdbImageUtils.logo(anime.overrideLogoPath);
    }

    /**
     * Aplica os overrides sobre um {@link AnimeResponse} já montado pelo mapper trivial —
     * sobrescreve título, sinopse, thumbnails, banner e logo com os valores resolvidos.
     * Reaproveitado por qualquer service que monte um {@code AnimeResponse} a partir de um
     * {@link Anime} (base, listagem de raízes, ...).
     *
     * @param response resposta já mapeada, com os dados crus da AniList
     * @param anime model de onde vêm os overrides
     * @return o mesmo {@code response}, com os campos overridable resolvidos
     */
    public AnimeResponse applyTo(AnimeResponse response, Anime anime) {
        response.getTitles().main(resolveTitle(anime));
        response.synopsis(resolveSynopsis(anime));

        response.getThumbnails()
                .small(resolveThumbnailSmall(anime))
                .medium(resolveThumbnailMedium(anime))
                .large(resolveThumbnailLarge(anime));

        response.banner(resolveBanner(anime));
        response.logo(resolveLogo(anime));

        return response;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

}
