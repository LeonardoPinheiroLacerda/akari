package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

/** Flags do muxer HLS do ffmpeg ({@code -hls_flags}). */
public enum HlsFlag {
    INDEPENDENT_SEGMENTS,
    PROGRAM_DATE_TIME,
    DELETE_SEGMENTS,
    APPEND_LIST,
    TEMP_FILE
}
