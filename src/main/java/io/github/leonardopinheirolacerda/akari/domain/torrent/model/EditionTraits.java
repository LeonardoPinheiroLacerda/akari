package io.github.leonardopinheirolacerda.akari.domain.torrent.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TorrentTrackType;

/**
 * Características técnicas de uma edição, tiradas dos mesmos descritores do título que formam a
 * chave da edição. Cada uma é {@code null} quando o título não traz o descritor.
 *
 * @param resolution resolução, em minúsculas (ex.: {@code "1080p"})
 * @param source serviço de origem do vídeo (ex.: {@code "CR"})
 * @param medium mídia de origem (ex.: {@code "WEB-DL"})
 * @param audioType áudio em mais de um idioma; {@code null} quando o título não indica
 * @param audioCodec codec de áudio como aparece no título (ex.: {@code "AAC2.0"})
 * @param videoCodec codec de vídeo normalizado: {@code "H.264"}, {@code "H.265"} ou {@code "AV1"}
 * @param bitDepth profundidade de cor ou HDR como aparece no título (ex.: {@code "10bit"})
 */
public record EditionTraits(
        String resolution,
        String source,
        String medium,
        TorrentTrackType audioType,
        String audioCodec,
        String videoCodec,
        String bitDepth
) {
}
