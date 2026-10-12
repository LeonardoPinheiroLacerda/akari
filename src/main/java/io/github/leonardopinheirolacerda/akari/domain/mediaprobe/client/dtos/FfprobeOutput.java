package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Raiz do JSON produzido por {@code ffprobe -print_format json -show_format -show_streams}.
 *
 * @param format metadados do container
 * @param streams streams do arquivo (vídeo, áudio e legenda misturados, discriminados por
 *                {@link FfprobeStream#codecType()})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobeOutput(
        FfprobeFormat format,
        List<FfprobeStream> streams
) {
}
