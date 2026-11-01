package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Item de {@code packets} da saída do {@code ffprobe -show_entries packet=pts_time,flags}.
 *
 * <p>{@code ptsTime} fica {@code String} pela mesma razão de {@link FfprobeFormat}: o ffprobe
 * emite os números entre aspas no JSON.
 *
 * @param ptsTime instante de apresentação do pacote, em segundos, como string — absoluto, sem
 *                descontar o {@code start_time} do arquivo
 * @param flags flags do pacote; contém {@code K} quando o pacote é keyframe (ex.: {@code "K__"})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobePacket(
        @JsonProperty("pts_time") String ptsTime,
        String flags
) {
}
