package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/**
 * Decisões de áudio. Em {@link TranscodeMode#COPY} os campos de encode abaixo são ignorados.
 *
 * @param mode remux ({@code COPY}) ou reencode ({@code ENCODE})
 * @param codec codec-alvo no encode; {@code null} se {@code COPY}
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
