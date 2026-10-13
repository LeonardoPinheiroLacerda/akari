package io.github.leonardopinheirolacerda.akari.domain.playback.resource;

import io.github.leonardopinheirolacerda.akari.api.PlaybackApi;
import io.github.leonardopinheirolacerda.akari.api.dto.PlaybackSessionResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.SeekRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SessionHeartbeat;
import io.github.leonardopinheirolacerda.akari.api.dto.StartPlaybackSessionRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.SwitchTracksRequest;
import io.github.leonardopinheirolacerda.akari.domain.playback.service.PlaybackService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jboss.resteasy.reactive.server.ServerResponseFilter;
import org.jboss.resteasy.reactive.server.SimpleResourceInfo;

import java.io.File;
import java.util.UUID;

/**
 * Implementa {@link PlaybackApi} — sessões de reprodução (transcode HLS sob demanda) em
 * {@code /v1/playback/sessions}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda a orquestração do transcode fica no
 * {@link PlaybackService}. O filtro de resposta no final da classe adiciona o que a interface
 * gerada não tem como expressar sozinha — o header de TTL da playlist e o Content-Type por
 * extensão do arquivo de mídia — mesmo mecanismo já usado em
 * {@code StorageProviderResource.addLocationHeader}.
 */
@ApplicationScoped
public class PlaybackResource implements PlaybackApi {

    @Inject
    PlaybackService playbackService;

    /**
     * {@code POST /v1/playback/sessions} — inicia uma sessão de transcode.
     *
     * @param startPlaybackSessionRequest arquivo e preferências de transcode da sessão
     * @return a sessão recém-criada, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir no
     *         catalog, ou se o arquivo de provider remoto ainda não tiver terminado de baixar
     *         no cache
     * @throws BusinessRuleException (traduzida em 400) se o {@code profile} informado for
     *         desconhecido, se nenhuma estratégia de transcode se aplicar a ele, ou se os
     *         índices de faixa não existirem no arquivo
     * @throws IntegrationNotConfiguredException (traduzida em 503) se
     *         {@code playback.work.base-dir} não estiver configurado
     * @throws IntegrationException (traduzida em 502) se o ffmpeg falhar ao iniciar
     */
    @Override
    public PlaybackSessionResponse startPlaybackSession(StartPlaybackSessionRequest startPlaybackSessionRequest) {
        return playbackService.startPlaybackSession(startPlaybackSessionRequest);
    }

    /**
     * {@code POST /v1/playback/sessions/{sessionId}/seek} — reposiciona o transcode de uma
     * sessão já iniciada.
     *
     * @param sessionId id da sessão de playback
     * @param seekRequest instante alvo do reposicionamento
     * @return a sessão atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a sessão não existe ou já expirou
     */
    @Override
    public PlaybackSessionResponse seekPlaybackSession(UUID sessionId, SeekRequest seekRequest) {
        return playbackService.seekPlaybackSession(sessionId, seekRequest);
    }

    /**
     * {@code POST /v1/playback/sessions/{sessionId}/tracks} — troca as faixas de áudio/legenda
     * de uma sessão já iniciada.
     *
     * @param sessionId id da sessão de playback
     * @param switchTracksRequest instante de retomada e as novas faixas de áudio/legenda
     * @return a sessão atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a sessão não existe ou já expirou
     * @throws BusinessRuleException (traduzida em 400) se os novos índices de faixa não
     *         existirem no arquivo
     */
    @Override
    public PlaybackSessionResponse switchPlaybackSessionTracks(UUID sessionId, SwitchTracksRequest switchTracksRequest) {
        return playbackService.switchPlaybackSessionTracks(sessionId, switchTracksRequest);
    }

    /**
     * {@code GET /v1/playback/sessions/{sessionId}/playlist.m3u8} — serve a playlist HLS da
     * sessão.
     *
     * @param sessionId id da sessão de playback
     * @return a playlist {@code m3u8}, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a sessão não existe, expirou ou
     *         ainda não produziu playlist
     */
    @Override
    public String getPlaybackPlaylist(UUID sessionId) {
        return playbackService.getPlaybackPlaylist(sessionId);
    }

    /**
     * {@code GET /v1/playback/sessions/{sessionId}/{file}} — serve um arquivo HLS referenciado
     * pela playlist da sessão.
     *
     * @param sessionId id da sessão de playback
     * @param _file nome do arquivo referenciado pela playlist
     * @return o arquivo, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a sessão não existe, expirou, o
     *         nome não corresponde a um arquivo aceito, ou o arquivo ainda não foi gerado
     */
    @Override
    public File getPlaybackMediaFile(UUID sessionId, String _file) {
        return playbackService.getPlaybackMediaFile(sessionId, _file);
    }

    /**
     * {@code POST /v1/playback/sessions/{sessionId}/heartbeat} — mantém a sessão viva.
     *
     * @param sessionId id da sessão de playback
     * @return a validade restante da sessão, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a sessão não existe ou já expirou
     */
    @Override
    public SessionHeartbeat heartbeatPlaybackSession(UUID sessionId) {
        return playbackService.heartbeatPlaybackSession(sessionId);
    }

    /**
     * Completa a resposta de {@link #getPlaybackPlaylist} e {@link #getPlaybackMediaFile} com o
     * que a interface gerada não expressa: o header de TTL da playlist, o Content-Type correto
     * por extensão do arquivo de mídia (RESTEasy Reactive não escolhe sozinho entre os 4
     * {@code @Produces} declarados quando o cliente manda um Accept genérico, comum em tags
     * {@code video}/{@code audio}), e o suporte a byte-range (o HLS nativo do Safari exige
     * {@code 206}/{@code Accept-Ranges} nos segmentos). O body writer de {@code File} do
     * RESTEasy Reactive já corta os bytes e adiciona {@code Content-Range} sozinho quando o
     * header {@code Range} chega, mas só troca o status durante a escrita do corpo — tarde
     * demais pra resposta já commitada como {@code 200}; por isso o status é decidido aqui.
     *
     * @param requestContext requisição original, usado pra ler o header {@code Range}
     * @param responseContext resposta em construção
     * @param simpleResourceInfo metadados do método do resource que gerou a resposta
     * @param uriInfo usado pra ler o nome do arquivo do path, no caso de
     *                {@code getPlaybackMediaFile}
     */
    @ServerResponseFilter
    void applyPlaybackResponseHeaders(ContainerRequestContext requestContext,
                                      ContainerResponseContext responseContext,
                                      SimpleResourceInfo simpleResourceInfo,
                                      UriInfo uriInfo) {

        if (!responseContext.getStatusInfo().getFamily().equals(Response.Status.Family.SUCCESSFUL)) {
            return;
        }

        if ("getPlaybackPlaylist".equals(simpleResourceInfo.getMethodName())) {
            final Long heartbeatTtlSeconds = playbackService.getHeartbeatTtlSeconds();
            responseContext.getHeaders().add("X-Playback-Heartbeat-Ttl-Seconds", heartbeatTtlSeconds);
            return;
        }

        if ("getPlaybackMediaFile".equals(simpleResourceInfo.getMethodName())) {
            final String fileName = uriInfo.getPathParameters().getFirst("file");

            responseContext
                    .getHeaders()
                    .putSingle("Content-Type", contentTypeFor(fileName));

            responseContext
                    .getHeaders()
                    .putSingle("Accept-Ranges", "bytes");

            if (requestContext.getHeaders().getFirst("Range") != null) {
                responseContext.setStatusInfo(Response.Status.PARTIAL_CONTENT);
            }
        }
    }

    private String contentTypeFor(String fileName) {
        if (fileName == null) {
            return "application/octet-stream";
        }

        if (fileName.endsWith(".ts")) {
            return "video/mp2t";
        }

        if (fileName.endsWith(".m3u8")) {
            return "application/vnd.apple.mpegurl";
        }

        if (fileName.endsWith(".vtt")) {
            return "text/vtt";
        }

        if (fileName.endsWith(".m4s") || fileName.equals("init.mp4")) {
            return "video/mp4";
        }

        return "application/octet-stream";
    }

}
