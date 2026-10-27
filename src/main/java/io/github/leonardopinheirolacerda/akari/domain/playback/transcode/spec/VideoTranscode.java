package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

import java.util.Set;

/**
 * Decisões de vídeo. Em {@link TranscodeMode#COPY} (remux) os campos de encode abaixo são
 * ignorados, <strong>exceto</strong> se a legenda escolhida na sessão exigir burn-in (texto ou
 * imagem) — nesse caso o {@code FfmpegClient} cede pra encode, já que stream copy não decodifica
 * pra aplicar o filtro de queima, e usa os campos de encode abaixo como fallback. Por isso toda
 * estratégia que escolher {@code COPY} deve preenchê-los com uma configuração de encode válida,
 * mesmo que {@code COPY} seja o caminho preferido.
 *
 * @param mode remux preferido ({@code COPY}) ou reencode ({@code ENCODE})
 * @param codec codec-alvo no encode; usado tanto no modo {@code ENCODE} quanto como fallback do
 *              {@code COPY} quando a legenda exige burn-in
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
