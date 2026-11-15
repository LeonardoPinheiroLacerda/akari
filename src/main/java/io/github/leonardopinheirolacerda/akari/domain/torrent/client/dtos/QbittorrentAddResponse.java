package io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Resposta de {@code POST /api/v2/torrents/add} do qBittorrent, só com o campo usado.
 *
 * @param failureCount quantos torrents do pedido foram recusados
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record QbittorrentAddResponse(
        @JsonProperty("failure_count") Integer failureCount
) {
}
