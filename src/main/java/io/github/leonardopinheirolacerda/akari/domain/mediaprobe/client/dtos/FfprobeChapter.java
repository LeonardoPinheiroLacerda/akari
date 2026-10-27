package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Um item de {@code chapters} da saída do {@code ffprobe}.
 *
 * <p>{@code startTime}/{@code endTime} ficam {@code String} pela mesma razão de
 * {@link FfprobeFormat}. O {@code id} do ffprobe não entra aqui — o contrato não precisa dele.
 *
 * @param startTime instante de início do capítulo, em segundos, como string
 * @param endTime instante de fim do capítulo, em segundos, como string
 * @param tags tags do capítulo (chave {@code title}, entre outras)
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobeChapter(
        @JsonProperty("start_time") String startTime,
        @JsonProperty("end_time") String endTime,
        Map<String, String> tags
) {
}
