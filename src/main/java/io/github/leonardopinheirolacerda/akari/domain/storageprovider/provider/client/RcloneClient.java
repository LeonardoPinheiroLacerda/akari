package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client;

import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyFileRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyFileResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyJobStatusRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyJobStatusResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneDownloadStatsRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneDownloadStatsResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneListRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneListResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneObscureRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneObscureResponse;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client (MicroProfile) que mapeia os endpoints do rclone remote control daemon (rcd).
 *
 * <p>A URL base é resolvida pela chave {@code quarkus.rest-client.rclone.url}. Todas as
 * chamadas são POST com JSON, respeitando o contrato do rcd.
 *
 * <p>Esta interface é o transporte cru — não faz retry, guard, nem tradução de exceções.
 * Fault tolerance e embrulho em {@code IntegrationException} ficam no service que chamar.
 */
@RegisterRestClient(configKey = "rclone")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public interface RcloneClient {

    /**
     * Lista arquivos e diretórios no path informado.
     *
     * @param request contém o filesystem descriptor, o path e as opções (recursivo, etc.)
     * @return resposta com a lista de itens encontrados
     */
    @POST
    @Path("/operations/list")
    RcloneListResponse listFiles(RcloneListRequest request);

    /**
     * Copia um arquivo remoto para um destino local. O flag {@code async} do request decide
     * o modo: {@code true} retorna o {@code jobid} imediatamente, pra acompanhamento;
     * {@code false} bloqueia até o fim da cópia (e a resposta não traz {@code jobid} útil).
     *
     * @param request contém o filesystem de origem, o arquivo remoto, o diretório e nome
     *                locais e o flag {@code async}
     * @return resposta com o {@code jobid} do job assíncrono criado, quando {@code async}
     *         for {@code true}
     */
    @POST
    @Path("/operations/copyfile")
    RcloneCopyFileResponse downloadFile(RcloneCopyFileRequest request);

    /**
     * Consulta o estado (finalizado, sucesso, erro, duração) de um job assíncrono.
     *
     * @param request contém o {@code jobid} a consultar
     * @return resposta com os metadados do job
     */
    @POST
    @Path("/job/status")
    RcloneCopyJobStatusResponse getJobStatus(RcloneCopyJobStatusRequest request);

    /**
     * Consulta o progresso de transferência (bytes movidos, velocidade, ETA) agrupado por
     * job.
     *
     * @param request identifica o grupo do job no formato {@code job/<jobid>}
     * @return resposta com as estatísticas de transferência
     */
    @POST
    @Path("/core/stats")
    RcloneDownloadStatsResponse getTransferProgress(RcloneDownloadStatsRequest request);

    /**
     * Obscurece um valor em claro (formato reversível do rclone).
     *
     * @param request contém o valor em claro
     * @return resposta com o valor obscurecido
     */
    @POST
    @Path("/core/obscure")
    RcloneObscureResponse obscure(RcloneObscureRequest request);

    /**
     * Verifica se o rcd está atendendo requisições. Chama {@code /rc/noop} — endpoint padrão
     * do rclone que só responde 200 vazio.
     *
     * @throws ProcessingException se o transporte HTTP falhar (ex.: conexão
     *         recusada, timeout — indica que o rcd está fora do ar)
     * @throws WebApplicationException se o rcd responder com status HTTP de
     *         erro
     */
    @POST
    @Path("/rc/noop")
    void healthCheck();
}
