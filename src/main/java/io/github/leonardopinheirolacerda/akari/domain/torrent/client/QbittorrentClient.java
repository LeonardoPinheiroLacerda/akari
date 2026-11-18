package io.github.leonardopinheirolacerda.akari.domain.torrent.client;

import io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos.QbittorrentAddResponse;
import io.github.leonardopinheirolacerda.akari.domain.torrent.client.dtos.QbittorrentTorrentResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.List;

/**
 * REST Client (MicroProfile) da Web API v2 do qBittorrent.
 *
 * <p>A URL base é resolvida pela chave {@code quarkus.rest-client.qbittorrent.url}. A sessão é
 * um cookie devolvido pelo login e repassado em cada chamada pelo header {@code Cookie}.
 *
 * <p>Esta interface é o transporte cru — não faz login, retry nem tradução de exceções; isso
 * fica no service que chamar.
 */
@RegisterRestClient(configKey = "qbittorrent")
@Path("/api/v2")
public interface QbittorrentClient {

    /**
     * Abre uma sessão na Web API.
     *
     * @param username usuário da WebUI
     * @param password senha da WebUI
     * @return a resposta crua, com o cookie de sessão em {@code Set-Cookie} quando o login é aceito
     */
    @POST
    @Path("/auth/login")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    Response login(@FormParam("username") String username, @FormParam("password") String password);

    /**
     * Adiciona um torrent por link magnet.
     *
     * @param cookie cookie de sessão
     * @param urls link magnet
     * @param savePath diretório de destino, como o container do qBittorrent enxerga
     * @param tags tags aplicadas ao torrent
     * @param contentLayout layout dos arquivos no destino ({@code NoSubfolder} grava direto em
     *                      {@code savePath}, sem a pasta raiz do torrent)
     * @return quantos torrents do pedido foram aceitos e recusados
     */
    @POST
    @Path("/torrents/add")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.APPLICATION_JSON)
    QbittorrentAddResponse addTorrent(@HeaderParam(HttpHeaders.COOKIE) String cookie,
                      @FormParam("urls") String urls,
                      @FormParam("savepath") String savePath,
                      @FormParam("tags") String tags,
                      @FormParam("contentLayout") String contentLayout);

    /**
     * Lista torrents pelos info-hashes.
     *
     * @param cookie cookie de sessão
     * @param hashes info-hashes separados por {@code |}
     * @return os torrents encontrados; os hashes que não estão no qBittorrent ficam de fora
     */
    @GET
    @Path("/torrents/info")
    @Produces(MediaType.APPLICATION_JSON)
    List<QbittorrentTorrentResponse> listTorrents(@HeaderParam(HttpHeaders.COOKIE) String cookie,
                                                  @QueryParam("hashes") String hashes);

    /**
     * Remove torrents do qBittorrent.
     *
     * @param cookie cookie de sessão
     * @param hashes info-hashes separados por {@code |}
     * @param deleteFiles se {@code true}, apaga também os arquivos do torrent que ainda estiverem
     *                    no destino
     * @return o corpo da resposta (vazio quando aceito)
     */
    @POST
    @Path("/torrents/delete")
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    @Produces(MediaType.TEXT_PLAIN)
    String deleteTorrents(@HeaderParam(HttpHeaders.COOKIE) String cookie,
                        @FormParam("hashes") String hashes,
                        @FormParam("deleteFiles") Boolean deleteFiles);
}
