package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

/**
 * Um item de {@code streams} da saída do {@code ffprobe} — shape único compartilhado por
 * stream de vídeo, áudio e legenda; {@code codecType} é o discriminador.
 *
 * <p>{@code bitRate}/{@code sampleRate} ficam {@code String} pela mesma razão de
 * {@link FfprobeFormat}. Só captura {@code avg_frame_rate} (não {@code r_frame_rate}) porque o
 * contrato usa exclusivamente essa fonte pro {@code frameRate}.
 *
 * @param index índice global do stream no arquivo, contando todos os tipos
 * @param codecName nome do codec, ex. {@code "h264"}, {@code "aac"}, {@code "subrip"}
 * @param codecType tipo do stream — {@code "video"}, {@code "audio"} ou {@code "subtitle"}
 * @param width largura em pixels; só se aplica a vídeo
 * @param height altura em pixels; só se aplica a vídeo
 * @param displayAspectRatio razão de aspecto de exibição, ex. {@code "16:9"}; só vídeo
 * @param avgFrameRate taxa de quadros média, como fração (ex. {@code "24000/1001"}); só vídeo
 * @param bitRate taxa de bits do stream em bits/segundo, como string
 * @param pixFmt formato de pixel, ex. {@code "yuv420p"}; só vídeo
 * @param profile perfil do codec, ex. {@code "High"}; só vídeo
 * @param channels quantidade de canais; só áudio
 * @param channelLayout layout dos canais, ex. {@code "stereo"}; só áudio
 * @param sampleRate taxa de amostragem em Hz, como string; só áudio
 * @param tags tags do stream (chaves {@code language}/{@code title}, entre outras)
 * @param disposition flags do stream (chaves {@code default}/{@code forced}/
 *                    {@code hearing_impaired}, valor {@code 1}/{@code 0})
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FfprobeStream(
        Integer index,
        @JsonProperty("codec_name") String codecName,
        @JsonProperty("codec_type") String codecType,
        Integer width,
        Integer height,
        @JsonProperty("display_aspect_ratio") String displayAspectRatio,
        @JsonProperty("avg_frame_rate") String avgFrameRate,
        @JsonProperty("bit_rate") String bitRate,
        @JsonProperty("pix_fmt") String pixFmt,
        String profile,
        Integer channels,
        @JsonProperty("channel_layout") String channelLayout,
        @JsonProperty("sample_rate") String sampleRate,
        Map<String, String> tags,
        Map<String, Integer> disposition
) {
}
