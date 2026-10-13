package io.github.leonardopinheirolacerda.akari.domain.playback.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.PlaybackSessionResponse;
import io.github.leonardopinheirolacerda.akari.domain.playback.model.PlaybackSession;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte {@link PlaybackSession} (model) pro DTO de resposta do contrato
 * ({@link PlaybackSessionResponse}).
 */
@Mapper(componentModel = "cdi")
public interface PlaybackMapper {

    @Mapping(target = "playlistUrl", expression = "java(\"/v1/playback/sessions/\" + entity.sessionId + \"/playlist.m3u8\")")
    PlaybackSessionResponse toResponse(PlaybackSession entity);

}
