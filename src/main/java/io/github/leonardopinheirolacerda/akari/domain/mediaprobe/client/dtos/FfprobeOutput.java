package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Raiz do JSON produzido por
 * {@code ffprobe -print_format json -show_format -show_streams -show_chapters}.
 *
 * @param format metadados do container
 * @param streams streams do arquivo (vídeo, áudio e legenda misturados, discriminados por
 *                {@link FfprobeStream#codecType()})
 * @param chapters capítulos do arquivo, na ordem do arquivo; lista vazia (ou ausente) se não
 *                 houver
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobeOutput(
        FfprobeFormat format,
        List<FfprobeStream> streams,
        List<FfprobeChapter> chapters
) {
}
