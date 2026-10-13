package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/** Ajuste do encoder para o tipo de conteúdo. {@code ANIMATION} é o indicado para anime. */
public enum VideoTune {
    FILM,
    ANIMATION,
    GRAIN,
    STILLIMAGE,
    FASTDECODE,
    ZEROLATENCY
}
