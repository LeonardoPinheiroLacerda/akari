package io.github.leonardopinheirolacerda.akari.domain.torrent.provider.model;

import java.time.OffsetDateTime;

/**
 * Release como lida da página de busca de um provedor de torrent, antes de qualquer
 * interpretação do título.
 *
 * @param infoHash info-hash BitTorrent, 40 caracteres hexadecimais minúsculos
 * @param magnetUri link magnet como o provedor publica, com os trackers do próprio site
 * @param title título da release como publicado no provedor
 * @param size tamanho como o provedor exibe (ex.: {@code "1.4 GiB"}); {@code null} se ausente
 * @param seeders seeders no momento da leitura; {@code null} se ausente
 * @param publishedAt quando a release foi publicada; {@code null} se ausente
 * @param pageUrl página da release no provedor; {@code null} se ausente
 */
public record ScrapedRelease(
        String infoHash,
        String magnetUri,
        String title,
        String size,
        Integer seeders,
        OffsetDateTime publishedAt,
        String pageUrl
) {
}
