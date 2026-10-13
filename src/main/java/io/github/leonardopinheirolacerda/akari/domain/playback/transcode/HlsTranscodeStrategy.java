package io.github.leonardopinheirolacerda.akari.domain.playback.transcode;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.domain.playback.transcode.spec.HlsTranscodeSpec;

import java.util.Set;

/**
 * Estratégia de transcode HLS (regra "condição → consequência" como código). As implementações
 * são beans CDI descobertos automaticamente — adicionar uma nova estratégia é criar uma classe;
 * não há registro central.
 *
 * <p>Para um vídeo, avaliam-se as estratégias do profile escolhido; cada uma dá uma nota via
 * {@link #score(MediaInfoResponse)} e a de maior nota vence, gerando o HLS a partir da sua
 * {@link HlsTranscodeSpec}.
 */
public interface HlsTranscodeStrategy {

    /**
     * Profiles a que esta estratégia pertence. Uma estratégia pode servir a mais de um profile.
     *
     * @return conjunto não vazio de {@link Profile}
     */
    Set<Profile> profiles();

    /**
     * Nota de aderência da estratégia ao vídeo. Especificidade vira nota: uma estratégia
     * específica pontua alto, um fallback pontua baixo.
     *
     * @param probe metadados técnicos do vídeo (codec/bitrate/resolução) extraídos pelo mediaprobe
     * @return nota da estratégia; {@code <= 0} significa que ela se abstém (não se aplica)
     */
    int score(MediaInfoResponse probe);

    /**
     * Monta a intenção de transcode desta estratégia para o vídeo, quando ela é a vencedora.
     *
     * @param probe metadados técnicos do vídeo
     * @return spec estruturada que o {@code FfmpegClient} vai consumir
     */
    HlsTranscodeSpec buildSpec(MediaInfoResponse probe);

}
