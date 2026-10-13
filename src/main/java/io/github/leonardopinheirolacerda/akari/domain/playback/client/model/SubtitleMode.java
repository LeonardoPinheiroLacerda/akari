package io.github.leonardopinheirolacerda.akari.domain.playback.client.model;

/**
 * Como a faixa de legenda escolhida é entregue no transcode.
 */
public enum SubtitleMode {

    /** Nenhuma faixa de legenda selecionada. */
    NONE,

    /** Faixa de texto (SRT) entregue como WebVTT soft, numa variante própria do HLS. */
    SOFT,

    /** Faixa não textual (ex.: ASS) queimada na imagem do vídeo. */
    BURN

}
