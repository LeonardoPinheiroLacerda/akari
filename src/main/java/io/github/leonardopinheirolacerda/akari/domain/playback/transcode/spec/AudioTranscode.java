package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/**
 * Decisões de áudio. Em {@link TranscodeMode#COPY} os campos de encode abaixo são ignorados,
 * <strong>exceto</strong> {@code codec} — usado pelo {@code FfmpegClient} como fallback de
 * encode se a faixa de áudio realmente selecionada na sessão não estiver de fato nesse codec
 * (a estratégia decide {@code COPY} avaliando o probe inteiro, sem saber qual faixa o cliente
 * vai selecionar). Por isso toda estratégia que escolher {@code COPY} deve preencher
 * {@code codec} com o codec-alvo de qualquer forma.
 *
 * @param mode remux preferido ({@code COPY}) ou reencode ({@code ENCODE})
 * @param codec codec-alvo no encode; usado tanto no modo {@code ENCODE} quanto como fallback do
 *              {@code COPY} quando a faixa selecionada não é desse codec
 * @param bitrateKbps bitrate em kbps; {@code null} se não especificado
 * @param channels número de canais de saída (ex.: {@code 2} para downmix estéreo); {@code null}
 *                 se não especificado
 */
public record AudioTranscode(
        TranscodeMode mode,
        AudioCodec codec,
        Integer bitrateKbps,
        Integer channels
) {
}
