package io.github.leonardopinheirolacerda.akari.domain.playback.resource;

import io.github.leonardopinheirolacerda.akari.api.PlaybackApi;
import io.github.leonardopinheirolacerda.akari.api.dto.PlaybackSession;
import io.github.leonardopinheirolacerda.akari.api.dto.SeekRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SessionHeartbeat;
import io.github.leonardopinheirolacerda.akari.api.dto.StartPlaybackSessionRequest;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.io.File;
import java.util.UUID;

public class PlaybackResource implements PlaybackApi {

    // Header: Content-Type escolhido pela extensão (.ts → video/mp2t; .m4s e init.mp4 → video/mp4;
    // .m3u8 → application/vnd.apple.mpegurl; .vtt → text/vtt). O legado também mandava
    // `Accept-Ranges: bytes`.
    // 404: sessão inexistente ou expirada, nome fora de MediaFileName, ou arquivo ainda não gerado
    // (segmento além da borda ao vivo — o player retenta).
    @Override
    public File getPlaybackMediaFile(UUID sessionId, String _file) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Header: `X-Playback-Heartbeat-Ttl-Seconds` com o TTL do heartbeat (preferência do admin), o
    // mesmo `expiresInSeconds` do heartbeat. Content-Type: application/vnd.apple.mpegurl.
    // 404: sessão inexistente, expirada ou que ainda não produziu playlist.
    @Override
    public String getPlaybackPlaylist(UUID sessionId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 404: sessão inexistente ou expirada.
    @Override
    public SessionHeartbeat heartbeatPlaybackSession(UUID sessionId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 404: sessão inexistente ou expirada.
    @Override
    public PlaybackSession seekPlaybackSession(UUID sessionId, SeekRequest seekRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 400: profile de transcode desconhecido.
    // 404: arquivo de provider remoto ainda não terminou de baixar no cache.
    @Override
    public PlaybackSession startPlaybackSession(
            StartPlaybackSessionRequest startPlaybackSessionRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
