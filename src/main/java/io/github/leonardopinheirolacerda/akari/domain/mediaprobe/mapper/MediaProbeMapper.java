package io.github.leonardopinheirolacerda.akari.domain.mediaprobe.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.AudioTrack;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaContainer;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaInfoResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.SubtitleTrack;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoTrack;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeFormat;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeOutput;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.client.dtos.FfprobeStream;
import io.github.leonardopinheirolacerda.akari.domain.mediaprobe.parser.FfprobeValueParser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;
import java.util.Map;

/**
 * Converte a saída crua do ffprobe ({@link FfprobeOutput}) no DTO de resposta do contrato
 * ({@code MediaInfoResponse}).
 *
 * <p>As conversões não triviais (número-em-string, frame rate, tags, disposition) delegam pros
 * métodos {@code @Named} abaixo, que por sua vez chamam {@link FfprobeValueParser} — mesmo
 * padrão do {@code TmdbMapper} com {@code TmdbImageUtils}. O particionamento de
 * {@code FfprobeStream} por {@code codecType} (vídeo/áudio/legenda) segue o mesmo mecanismo:
 * métodos {@code @Named} que filtram a lista e reusam {@code toVideoTrack}/{@code toAudioTrack}/
 * {@code toSubtitleTrack} por elemento — MapStruct não tem primitiva pra dividir uma lista por
 * discriminador, mas isso continua sendo só conversão de formato, sem regra de negócio.
 */
@Mapper(componentModel = "cdi")
public interface MediaProbeMapper {

    @Mapping(source = "format", target = "container")
    @Mapping(source = "streams", target = "video", qualifiedByName = "toVideoTracks")
    @Mapping(source = "streams", target = "audio", qualifiedByName = "toAudioTracks")
    @Mapping(source = "streams", target = "subtitles", qualifiedByName = "toSubtitleTracks")
    MediaInfoResponse toResponse(FfprobeOutput output);

    @Mapping(source = "duration", target = "durationSeconds", qualifiedByName = "toDouble")
    @Mapping(source = "size", target = "sizeBytes", qualifiedByName = "toLong")
    @Mapping(source = "bitRate", target = "bitRate", qualifiedByName = "toLong")
    MediaContainer toContainer(FfprobeFormat format);

    @Mapping(source = "codecName", target = "codec")
    @Mapping(source = "avgFrameRate", target = "frameRate", qualifiedByName = "frameRate")
    @Mapping(source = "bitRate", target = "bitRate", qualifiedByName = "toLong")
    @Mapping(source = "tags", target = "language", qualifiedByName = "language")
    @Mapping(source = "tags", target = "title", qualifiedByName = "title")
    @Mapping(source = "disposition", target = "defaultTrack", qualifiedByName = "defaultFlag")
    VideoTrack toVideoTrack(FfprobeStream stream);

    @Mapping(source = "codecName", target = "codec")
    @Mapping(source = "sampleRate", target = "sampleRate", qualifiedByName = "toInteger")
    @Mapping(source = "bitRate", target = "bitRate", qualifiedByName = "toLong")
    @Mapping(source = "tags", target = "language", qualifiedByName = "language")
    @Mapping(source = "tags", target = "title", qualifiedByName = "title")
    @Mapping(source = "disposition", target = "defaultTrack", qualifiedByName = "defaultFlag")
    AudioTrack toAudioTrack(FfprobeStream stream);

    @Mapping(source = "codecName", target = "codec")
    @Mapping(source = "tags", target = "language", qualifiedByName = "language")
    @Mapping(source = "tags", target = "title", qualifiedByName = "title")
    @Mapping(source = "disposition", target = "defaultTrack", qualifiedByName = "defaultFlag")
    @Mapping(source = "disposition", target = "forced", qualifiedByName = "forcedFlag")
    @Mapping(source = "disposition", target = "hearingImpaired", qualifiedByName = "hearingImpairedFlag")
    SubtitleTrack toSubtitleTrack(FfprobeStream stream);

    @Named("toVideoTracks")
    default List<VideoTrack> toVideoTracks(List<FfprobeStream> streams) {
        return streams.stream()
                .filter(stream -> "video".equals(stream.codecType()))
                .map(this::toVideoTrack)
                .toList();
    }

    @Named("toAudioTracks")
    default List<AudioTrack> toAudioTracks(List<FfprobeStream> streams) {
        return streams.stream()
                .filter(stream -> "audio".equals(stream.codecType()))
                .map(this::toAudioTrack)
                .toList();
    }

    @Named("toSubtitleTracks")
    default List<SubtitleTrack> toSubtitleTracks(List<FfprobeStream> streams) {
        return streams.stream()
                .filter(stream -> "subtitle".equals(stream.codecType()))
                .map(this::toSubtitleTrack)
                .toList();
    }

    @Named("toDouble")
    default Double toDouble(String value) {
        return FfprobeValueParser.toDouble(value);
    }

    @Named("toLong")
    default Long toLong(String value) {
        return FfprobeValueParser.toLong(value);
    }

    @Named("toInteger")
    default Integer toInteger(String value) {
        return FfprobeValueParser.toInteger(value);
    }

    @Named("frameRate")
    default Double frameRate(String value) {
        return FfprobeValueParser.frameRate(value);
    }

    @Named("language")
    default String language(Map<String, String> tags) {
        return FfprobeValueParser.language(tags);
    }

    @Named("title")
    default String title(Map<String, String> tags) {
        return FfprobeValueParser.title(tags);
    }

    @Named("defaultFlag")
    default Boolean defaultFlag(Map<String, Integer> disposition) {
        return FfprobeValueParser.defaultFlag(disposition);
    }

    @Named("forcedFlag")
    default Boolean forcedFlag(Map<String, Integer> disposition) {
        return FfprobeValueParser.forcedFlag(disposition);
    }

    @Named("hearingImpairedFlag")
    default Boolean hearingImpairedFlag(Map<String, Integer> disposition) {
        return FfprobeValueParser.hearingImpairedFlag(disposition);
    }

}
