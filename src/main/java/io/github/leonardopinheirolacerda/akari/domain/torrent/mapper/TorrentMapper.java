package io.github.leonardopinheirolacerda.akari.domain.torrent.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TorrentRelease;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte os models do domínio {@code torrent} para os DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface TorrentMapper {

    /**
     * Converte uma release interpretada no DTO de resposta da busca.
     *
     * @param parsedRelease release lida do provedor, com o título interpretado
     * @return o DTO da release
     */
    @Mapping(target = ".", source = "scrapedRelease")
    TorrentReleaseResponse toResponse(ParsedRelease parsedRelease);

    /**
     * Converte uma release baixada no DTO de download, sem o anime — o card do anime é montado
     * pelo domínio {@code anime}, com os overrides de título e capa.
     *
     * @param torrentRelease release enviada ao qBittorrent
     * @return o DTO do download, com {@code anime} vazio
     */
    @Mapping(target = "anime", ignore = true)
    TorrentDownloadResponse toDownloadResponse(TorrentRelease torrentRelease);
}
