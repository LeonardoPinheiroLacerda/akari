package io.github.leonardopinheirolacerda.akari.domain.torrent.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte os models do domínio {@code torrent} para os DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface TorrentMapper {

    /**
     * Converte uma release interpretada no DTO de resposta.
     *
     * @param parsedRelease release lida do provedor, com o título interpretado
     * @return o DTO da release
     */
    @Mapping(target = "infoHash", source = "scrapedRelease.infoHash")
    @Mapping(target = "magnetUri", source = "scrapedRelease.magnetUri")
    @Mapping(target = "title", source = "scrapedRelease.title")
    @Mapping(target = "size", source = "scrapedRelease.size")
    @Mapping(target = "seeders", source = "scrapedRelease.seeders")
    @Mapping(target = "publishedAt", source = "scrapedRelease.publishedAt")
    @Mapping(target = "pageUrl", source = "scrapedRelease.pageUrl")
    TorrentReleaseResponse toResponse(ParsedRelease parsedRelease);
}
