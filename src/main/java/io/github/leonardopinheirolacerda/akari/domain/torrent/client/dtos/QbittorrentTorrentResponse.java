package io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * Um torrent de {@code GET /api/v2/torrents/info} do qBittorrent, só com os campos usados.
 *
 * @param hash info-hash do torrent, em minúsculas
 * @param progress fração já baixada, de {@code 0} a {@code 1}
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QbittorrentTorrentResponse(
        String hash,
        Double progress
) {
}
