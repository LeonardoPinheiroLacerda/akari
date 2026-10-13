package io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec;

import java.util.Set;

/**
 * Parâmetros de segmentação e playlist HLS.
 *
 * @param segmentSeconds duração alvo de cada segmento, em segundos
 * @param segmentType container do segmento ({@code TS} ou {@code FMP4})
 * @param playlistType {@code VOD} (fechada) ou {@code EVENT} (cresce durante a ingestão)
 * @param listSize máximo de entradas na playlist ({@code 0} = mantém todas)
 * @param flags flags do muxer HLS a habilitar; {@code null}/vazio se nenhuma
 */
public record HlsOptions(
        int segmentSeconds,
        HlsSegmentType segmentType,
        HlsPlaylistType playlistType,
        int listSize,
        Set<HlsFlag> flags
) {
}
