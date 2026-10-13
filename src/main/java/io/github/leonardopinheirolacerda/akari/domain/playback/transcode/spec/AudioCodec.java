package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/**
 * Codec de áudio alvo no encode.
 *
 * <p>{@link #OPUS} e {@link #FLAC} exigem segmento {@link HlsSegmentType#FMP4} pra serem
 * reconhecidos pelo padrão HLS — em {@link HlsSegmentType#TS} o {@code FfmpegClient} ainda
 * produz o comando, mas loga aviso, porque o muxer MPEG-TS não tem tipo de stream oficial pra
 * nenhum dos dois.
 */
public enum AudioCodec {

    /** Padrão do mercado pra HLS; único reconhecido em segmento {@code TS}. */
    AAC,

    /** Lossless; sem bitrate-alvo — {@code bitrateKbps} da spec é ignorado pra esse codec. */
    FLAC,

    /** Alta eficiência a bitrates baixos; sem suporte nativo no AVPlayer da Apple. */
    OPUS

}
