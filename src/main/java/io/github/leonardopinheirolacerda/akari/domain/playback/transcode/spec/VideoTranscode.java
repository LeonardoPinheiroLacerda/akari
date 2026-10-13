package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

import java.util.Set;

/**
 * Decisões de vídeo. Em {@link TranscodeMode#COPY} (remux) todos os campos de encode abaixo são
 * ignorados.
 *
 * @param mode remux ({@code COPY}) ou reencode ({@code ENCODE})
 * @param codec codec-alvo no encode; {@code null} se {@code COPY}
 * @param rateControl controle de bitrate/qualidade base; {@code null} se não especificado
 * @param preset preset de velocidade↔compressão do encoder; {@code null} se não especificado
 * @param resolutions ladder de resoluções: uma rung = rendition única; várias = HLS adaptativo
 *                     (variants + master playlist); {@code null}/vazio = mantém a resolução de
 *                     origem, sem downscale. Rungs acima da resolução de origem são ignoradas
 * @param gopSeconds tamanho aproximado do GOP, em segundos; o {@code FfmpegClient} converte pro
 *                   número de frames da fonte e ajusta pra caber um número inteiro de vezes em
 *                   {@link HlsOptions#segmentSeconds}, garantindo segmentos HLS com GOP fechado
 *                   alinhado ao corte; {@code null} se não especificado (sem GOP forçado)
 * @param tune ajuste do encoder para o conteúdo (ex.: {@link VideoTune#ANIMATION}); {@code null}
 *             se não especificado
 */
public record VideoTranscode(
        TranscodeMode mode,
        VideoCodec codec,
        RateControl rateControl,
        EncodePreset preset,
        Set<Resolution> resolutions,
        Double gopSeconds,
        VideoTune tune
) {
}
