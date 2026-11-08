package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import java.util.List;

/**
 * Overrides gravados no anime pelo preenchimento automático a partir do TMDB (ver
 * {@code AnimeTmdbOverridesFillService#fillOverrides}).
 *
 * @param title título gravado; {@code null} se a setting de idioma está vazia ou nada foi
 *              encontrado
 * @param synopsis sinopse gravada; {@code null} se a setting de idioma está vazia ou nada foi
 *                 encontrado
 * @param backdropPath backdrop do anime — o que ele já tinha ou o escolhido agora; {@code null}
 *                     se nada foi encontrado
 * @param logoPath logo gravado; {@code null} se a lista de idiomas do logo está vazia ou
 *                 nada foi encontrado
 * @param notFound campos ({@code title}, {@code synopsis}, {@code backdropPath},
 *                 {@code logoPath}) sem opção no TMDB nos idiomas configurados
 */
public record TmdbOverridesFill(
        String title,
        String synopsis,
        String backdropPath,
        String logoPath,
        List<String> notFound
) {
}
