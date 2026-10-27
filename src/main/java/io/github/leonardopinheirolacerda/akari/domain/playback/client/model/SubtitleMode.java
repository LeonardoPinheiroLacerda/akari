package io.github.leonardopinheirolacerda.akari.domain.playback.client.model;

/**
 * Como a faixa de legenda escolhida é entregue no transcode.
 */
public enum SubtitleMode {

    /** Nenhuma faixa de legenda selecionada. */
    NONE,

    /** Faixa de texto (SRT) entregue como WebVTT soft, numa variante própria do HLS. */
    SOFT,

    /** Faixa de texto (ex.: ASS/SSA) queimada na imagem do vídeo pelo filtro {@code subtitles} (libass). */
    BURN_TEXT,

    /** Faixa de imagem (PGS, VobSub, DVB) queimada via {@code overlay} do stream de legenda decodificado. */
    BURN_IMAGE

}
