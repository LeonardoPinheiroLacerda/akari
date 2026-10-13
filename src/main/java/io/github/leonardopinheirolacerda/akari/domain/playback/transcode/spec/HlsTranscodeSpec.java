package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.HlsTranscodeStrategy;

/**
 * Intenção estruturada de transcode HLS produzida por uma {@link HlsTranscodeStrategy} — o
 * <em>como</em> codificar. Não é uma linha de comando ffmpeg crua nem o <em>o quê/onde</em>: o
 * arquivo de entrada, o diretório de saída, o offset de seek e as faixas escolhidas são contexto
 * de runtime, resolvido à parte pelo {@code PlaybackService}/{@code FfmpegClient}.
 *
 * <p>A aceleração de hardware não faz parte da spec: é preferência de ambiente do admin, lida do
 * settings no momento de subir o ffmpeg.
 *
 * @param video decisões de vídeo (remux ou encode)
 * @param audio decisões de áudio (remux ou encode)
 * @param hls parâmetros de segmentação/playlist HLS; nunca {@code null} — toda
 *            {@link HlsTranscodeStrategy} produz uma spec de transcode HLS
 */
public record HlsTranscodeSpec(
        VideoTranscode video,
        AudioTranscode audio,
        HlsOptions hls
) {
}

