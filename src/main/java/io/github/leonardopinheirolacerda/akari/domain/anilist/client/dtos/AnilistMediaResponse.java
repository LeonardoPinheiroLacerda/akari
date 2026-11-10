package io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos;

import java.util.List;

/**
 * Um {@code Media} devolvido pela AniList, tanto pela query de search (sem {@code relations},
 * pra manter o payload enxuto — vem {@code null}) quanto pela de fetch por id ({@code relations}
 * preenchido).
 *
 * @param id id do anime na AniList
 * @param idMal id correspondente do anime no MyAnimeList, ou {@code null} se não houver match
 * @param title bloco de títulos
 * @param synonyms sinônimos conhecidos
 * @param coverImage URLs de poster
 * @param description sinopse em HTML
 * @param episodes total de episódios, ou {@code null} se ainda não definido
 * @param duration duração média por episódio em minutos
 * @param startDate data de estreia (parcial)
 * @param endDate data de término (parcial); {@code null} enquanto o anime não terminou
 * @param status status bruto da AniList (ex.: {@code FINISHED}, {@code RELEASING})
 * @param seasonYear ano da estação de estreia
 * @param format formato bruto da AniList (ex.: {@code TV}, {@code MOVIE})
 * @param season estação bruta da AniList (ex.: {@code WINTER})
 * @param averageScore nota média ponderada em escala 0–100
 * @param meanScore nota média simples em escala 0–100
 * @param popularity quantidade de usuários com o anime na lista
 * @param isAdult flag NSFW
 * @param genres nomes dos gêneros
 * @param tags tags do anime, com relevância e marcação de spoiler; {@code null} na query de search
 * @param studios estúdios principais do anime (query já filtra {@code isMain: true})
 * @param relations relações com outros títulos; {@code null} na query de search
 */
public record AnilistMediaResponse(
        Integer id,
        Integer idMal,
        AnilistTitleResponse title,
        List<String> synonyms,
        AnilistCoverImageResponse coverImage,
        String description,
        Integer episodes,
        Integer duration,
        AnilistFuzzyDateResponse startDate,
        AnilistFuzzyDateResponse endDate,
        String status,
        Integer seasonYear,
        String format,
        String season,
        Integer averageScore,
        Integer meanScore,
        Integer popularity,
        Boolean isAdult,
        List<String> genres,
        List<AnilistTagResponse> tags,
        AnilistStudioConnectionResponse studios,
        AnilistRelationsResponse relations
) {
}
