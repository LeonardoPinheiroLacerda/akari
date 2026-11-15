package io.github.leonardopinheirolacerda.akari.domain.torrent.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentDownloadResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TorrentReleaseResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TrackedAnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.ParsedRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TorrentRelease;
import io.github.leonardopinheirolacerda.akari.domain.torrent.model.TrackedAnime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte os models do domínio {@code torrent} para os DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi")
public interface TorrentMapper {

    /**
     * Converte uma release interpretada no DTO de resposta da busca, sem o estado de download —
     * preenchido pelo service, que consulta os downloads de todas as releases de uma vez.
     *
     * @param parsedRelease release lida do provedor, com o título interpretado
     * @return o DTO da release, com {@code downloadStatus} vazio
     */
    @Mapping(target = ".", source = "scrapedRelease")
    @Mapping(target = "downloadStatus", ignore = true)
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

    /**
     * Converte um rastreio no DTO de resposta.
     *
     * @param trackedAnime rastreio
     * @return o DTO do rastreio
     */
    TrackedAnimeResponse toTrackedResponse(TrackedAnime trackedAnime);
}
