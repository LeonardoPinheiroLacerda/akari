package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Raiz do JSON produzido por
 * {@code ffprobe -print_format json -read_intervals ... -show_entries format=start_time:packet=pts_time,flags}.
 *
 * @param format metadados do container — só {@link FfprobeFormat#startTime()} vem preenchido
 * @param packets pacotes da janela lida, na ordem do arquivo; lista vazia (ou ausente) se a
 *                janela não tiver pacote
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobePacketsOutput(
        FfprobeFormat format,
        List<FfprobePacket> packets
) {
}
