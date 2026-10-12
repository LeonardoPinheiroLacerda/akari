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

/**
 * Implementa {@link PlaybackApi} — sessões de reprodução (transcode HLS sob demanda) em
 * {@code /v1/playback/sessions}.
 *
 * <p>Ainda não implementado — todos os métodos lançam {@code 501 Not Implemented}. Os comentários
 * de cada método documentam o comportamento já definido pelo contrato, pra guiar a implementação
 * futura.
 */
public class PlaybackResource implements PlaybackApi {

    /**
     * {@code GET /v1/playback/sessions/{sessionId}/{file}} — serve um arquivo HLS referenciado
     * pela playlist da sessão (variant playlist, segmento, init segment ou segmento de legenda).
     *
     * <p>Content-Type escolhido pela extensão: {@code .ts} → {@code video/mp2t}; {@code .m4s} e
     * {@code init.mp4} → {@code video/mp4}; {@code .m3u8} → {@code application/vnd.apple.mpegurl};
     * {@code .vtt} → {@code text/vtt}. O legado também mandava {@code Accept-Ranges: bytes}.
     *
     * @param sessionId id da sessão de playback
     * @param _file nome do arquivo referenciado pela playlist (ver {@code MediaFileName} no
     *              contrato pros nomes aceitos)
     * @return o arquivo, com {@code 200 OK}
     * @throws WebApplicationException (404) se a sessão não existe, expirou, o nome não
     *         corresponde a um arquivo aceito, ou o transcode ainda não o produziu — nesse
     *         último caso (segmento além da borda ao vivo), o player deve retentar
     */
    @Override
    public File getPlaybackMediaFile(UUID sessionId, String _file) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    /**
     * {@code GET /v1/playback/sessions/{sessionId}/playlist.m3u8} — serve a playlist HLS da
     * sessão.
     *
     * <p>Quando o transcode produz um master playlist (ABR com várias resoluções, ou legenda
     * soft), é ele que é devolvido — referenciando os variant playlists {@code stream_N.m3u8}.
     * Caso contrário, devolve a media playlist da rendition única. Header
     * {@code X-Playback-Heartbeat-Ttl-Seconds} informa o TTL do heartbeat (preferência do admin),
     * o mesmo {@code expiresInSeconds} devolvido por {@link #heartbeatPlaybackSession}.
     * Content-Type {@code application/vnd.apple.mpegurl}.
     *
     * @param sessionId id da sessão de playback
     * @return a playlist {@code m3u8}, com {@code 200 OK}
     * @throws WebApplicationException (404) se a sessão não existe, expirou ou ainda não
     *         produziu playlist
     */
    @Override
    public String getPlaybackPlaylist(UUID sessionId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    /**
     * {@code POST /v1/playback/sessions/{sessionId}/heartbeat} — mantém a sessão viva.
     *
     * <p>Ping periódico do cliente pra sinalizar que a sessão ainda está em uso: renova o TTL da
     * sessão (preferência do admin) e devolve a validade restante. O cliente deve chamar de novo
     * antes da validade expirar.
     *
     * @param sessionId id da sessão de playback
     * @return a validade restante da sessão, com {@code 200 OK}
     * @throws WebApplicationException (404) se a sessão não existe ou já expirou
     */
    @Override
    public SessionHeartbeat heartbeatPlaybackSession(UUID sessionId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    /**
     * {@code POST /v1/playback/sessions/{sessionId}/seek} — reposiciona o transcode de uma
     * sessão já iniciada.
     *
     * <p>A playlist {@code m3u8} passa a refletir a nova posição a partir do instante informado
     * em {@code seekRequest}.
     *
     * @param sessionId id da sessão de playback
     * @param seekRequest instante alvo do reposicionamento
     * @return a sessão atualizada, com {@code 200 OK}
     * @throws WebApplicationException (404) se a sessão não existe ou já expirou
     */
    @Override
    public PlaybackSession seekPlaybackSession(UUID sessionId, SeekRequest seekRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    /**
     * {@code POST /v1/playback/sessions} — inicia uma sessão de transcode.
     *
     * <p>Inicia o transcode da mídia (referenciada por {@code fileId}) com o perfil, faixa de
     * áudio e, opcionalmente, faixa de legenda escolhidos em {@code startPlaybackSessionRequest}.
     * Gera e devolve o {@code sessionId} (UUID) que identifica a sessão, junto do endereço da
     * playlist {@code m3u8}.
     *
     * @param startPlaybackSessionRequest arquivo e preferências de transcode da sessão
     * @return a sessão recém-criada, com {@code 201 Created}
     * @throws WebApplicationException (400) se o profile de transcode for desconhecido; (404)
     *         se o vídeo é de provider remoto e ainda não terminou de baixar no cache
     */
    @Override
    public PlaybackSession startPlaybackSession(
            StartPlaybackSessionRequest startPlaybackSessionRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
