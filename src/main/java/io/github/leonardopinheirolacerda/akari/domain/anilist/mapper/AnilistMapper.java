package io.github.leonardopinheirolacerda.akari.domain.anilist.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AnilistFormat;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistSeason;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistStudioConnectionResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Converte a resposta crua da AniList ({@link AnilistMediaResponse}) pro DTO de resposta do
 * contrato ({@code AnilistMediaSummary}).
 */
@Mapper(componentModel = "cdi")
public interface AnilistMapper {

    /**
     * Converte um item de mídia da AniList.
     *
     * @param media item cru retornado pela query GraphQL
     * @return o resumo convertido, com {@code format}/{@code season} resolvidos via
     *         {@link #toFormat}/{@link #toSeason}
     */
    @Mapping(source = "id", target = "anilistId")
    @Mapping(source = "idMal", target = "malId")
    @Mapping(target = "studio", expression = "java(toMainStudio(media.studios()))")
    AnilistMediaSummary toSummary(AnilistMediaResponse media);

    /**
     * Converte uma lista de itens de mídia da AniList.
     *
     * @param media itens crus retornados pela query GraphQL
     * @return os resumos convertidos, na mesma ordem
     */
    List<AnilistMediaSummary> toSummaryList(List<AnilistMediaResponse> media);

    /**
     * Converte o formato cru da AniList pro enum do contrato.
     *
     * @param format valor cru retornado pela AniList (string livre)
     * @return o enum correspondente; {@code null} se {@code format} for {@code null};
     *         {@link AnilistFormat#UNKNOWN} se o valor não bater com nenhuma constante
     *         conhecida — a whitelist existe exatamente pra cobrir esse caso sem estourar
     */
    default AnilistFormat toFormat(String format) {
        if (format == null) {
            return null;
        }
        try {
            return AnilistFormat.valueOf(format);
        } catch (IllegalArgumentException e) {
            return AnilistFormat.UNKNOWN;
        }
    }

    /**
     * Converte a temporada crua da AniList pro enum do contrato.
     *
     * @param season valor cru retornado pela AniList (string livre)
     * @return o enum correspondente; {@code null} se {@code season} for {@code null};
     *         {@link AnilistSeason#UNKNOWN} se o valor não bater com nenhuma constante
     *         conhecida — a whitelist existe exatamente pra cobrir esse caso sem estourar
     */
    default AnilistSeason toSeason(String season) {
        if (season == null) {
            return null;
        }
        try {
            return AnilistSeason.valueOf(season);
        } catch (IllegalArgumentException e) {
            return AnilistSeason.UNKNOWN;
        }
    }

    /**
     * Extrai o nome do estúdio principal, se a AniList tiver informado algum (a query já filtra
     * {@code isMain: true}); pega o primeiro caso haja mais de um.
     *
     * @param studios bloco cru de estúdios retornado pela AniList
     * @return o nome do estúdio principal, ou {@code null} se não houver nenhum
     */
    default String toMainStudio(AnilistStudioConnectionResponse studios) {
        if (studios == null || studios.nodes() == null || studios.nodes().isEmpty()) {
            return null;
        }
        return studios.nodes().get(0).name();
    }

}
