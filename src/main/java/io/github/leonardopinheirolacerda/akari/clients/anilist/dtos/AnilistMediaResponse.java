package io.github.leonardopinheirolacerda.akari.clients.anilist.dtos;

import java.util.List;

/**
 * Um {@code Media} devolvido pela AniList, tanto pela query de search (sem {@code relations},
 * pra manter o payload enxuto — vem {@code null}) quanto pela de fetch por id ({@code relations}
 * preenchido).
 *
 * @param id id do anime na AniList
 * @param title bloco de títulos
 * @param synonyms sinônimos conhecidos
 * @param coverImage URLs de poster
 * @param description sinopse em HTML
 * @param episodes total de episódios, ou {@code null} se ainda não definido
 * @param duration duração média por episódio em minutos
 * @param startDate data de estreia (parcial)
 * @param seasonYear ano da estação de estreia
 * @param format formato bruto da AniList (ex.: {@code TV}, {@code MOVIE})
 * @param season estação bruta da AniList (ex.: {@code WINTER})
 * @param averageScore nota média em escala 0–100
 * @param isAdult flag NSFW
 * @param genres nomes dos gêneros
 * @param relations relações com outros títulos; {@code null} na query de search
 */
public record AnilistMediaResponse(
        Integer id,
        AnilistTitleResponse title,
        List<String> synonyms,
        AnilistCoverImageResponse coverImage,
        String description,
        Integer episodes,
        Integer duration,
        AnilistFuzzyDateResponse startDate,
        Integer seasonYear,
        String format,
        String season,
        Integer averageScore,
        Boolean isAdult,
        List<String> genres,
        AnilistRelationsResponse relations
) {
}
