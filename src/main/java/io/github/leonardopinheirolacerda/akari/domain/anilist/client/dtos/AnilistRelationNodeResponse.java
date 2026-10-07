package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

/**
 * Anime do outro lado de uma relação (sequel, prequel, side story, ...).
 *
 * @param id id do anime relacionado na AniList
 * @param type tipo bruto da AniList (ex.: {@code ANIME}, {@code MANGA})
 * @param format formato bruto da AniList (ex.: {@code TV}, {@code MOVIE})
 * @param title bloco de títulos
 */
public record AnilistRelationNodeResponse(Integer id, String type, String format, AnilistTitleResponse title) {
}
