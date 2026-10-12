package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Bloco {@code format} da saída do {@code ffprobe}.
 *
 * <p>{@code duration}/{@code size}/{@code bitRate} ficam {@code String} porque o ffprobe os
 * emite entre aspas no JSON — a conversão pra tipo numérico só acontece no mapeamento pro DTO
 * de resposta do contrato. {@code @JsonIgnoreProperties(ignoreUnknown = true)} tolera campos
 * novos entre versões do ffprobe.
 *
 * @param formatName nome curto do container, ex. {@code "matroska,webm"}
 * @param formatLongName nome por extenso do container, ex. {@code "Matroska / WebM"}
 * @param duration duração em segundos, como string
 * @param size tamanho do arquivo em bytes, como string
 * @param bitRate taxa de bits do container em bits/segundo, como string
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobeFormat(
        @JsonProperty("format_name") String formatName,
        @JsonProperty("format_long_name") String formatLongName,
        String duration,
        String size,
        @JsonProperty("bit_rate") String bitRate
) {
}
