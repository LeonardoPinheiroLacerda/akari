package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;

/**
 * Aceleração de hardware para encode, resolvida da preferência
 * {@code playback.transcode.hardware-acceleration}. Escolha manual do admin — sem autodetecção
 * de plataforma. O encoder efetivo depende também do {@link VideoCodec} alvo (ver
 * {@code FfmpegClient}).
 */
public enum HwAccel {
    NONE,
    VIDEOTOOLBOX,
    NVENC,
    QSV,
    VAAPI;

    /**
     * Resolve a aceleração de hardware a partir do valor bruto da preferência.
     *
     * @param value valor da preferência {@code playback.transcode.hardware-acceleration}
     * @return a aceleração correspondente
     * @throws IntegrationNotConfiguredException se o valor não bater com nenhuma constante —
     *         é um problema de configuração do admin, não do request
     */
    public static HwAccel fromSetting(String value) {
        try {
            return HwAccel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IntegrationNotConfiguredException(
                    "Preferência 'playback.transcode.hardware-acceleration' inválida: '" + value + "'. "
                            + "Valores aceitos: NONE, VIDEOTOOLBOX, NVENC, QSV, VAAPI.");
        }
    }

}
