package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

/**
 * Profile de transcode HLS — agrupa estratégias ({@link HlsTranscodeStrategy}) e é a fonte da
 * verdade do que <em>existe</em> (via {@link #values()}).
 *
 * <p>O profile é escolhido pelo cliente ao iniciar a sessão; para um vídeo, avaliam-se as
 * estratégias que pertencem ao profile escolhido e a de maior nota vence.
 */
public enum Profile {

    /** Reprodução em navegador (hls.js). */
    WEB,

    /** Reprodução em TV / set-top box. */
    TV,

    /** Reprodução em dispositivo móvel. */
    MOBILE

}
