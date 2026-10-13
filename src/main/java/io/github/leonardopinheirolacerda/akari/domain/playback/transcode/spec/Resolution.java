package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/**
 * Rung de resolução da ladder ABR. Cada valor carrega as dimensões-alvo (16:9). Um conjunto com
 * mais de uma rung gera HLS adaptativo (variants + master playlist); com uma única, rendition
 * simples.
 */
public enum Resolution {

    UHD_2160(3840, 2160),
    QHD_1440(2560, 1440),
    FHD_1080(1920, 1080),
    HD_720(1280, 720),
    SD_480(854, 480),
    SD_360(640, 360);

    public final int width;
    public final int height;

    Resolution(int width, int height) {
        this.width = width;
        this.height = height;
    }

}
