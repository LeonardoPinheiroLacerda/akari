package io.github.leonardopinheirolacerda.akari.domain.ingestion.resource;

import io.github.leonardopinheirolacerda.akari.api.IngestionApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AvailableFolderNode;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestSubtreeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStartedResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class IngestionResource implements IngestionApi {

    // Só são síncronos o 400 do payload e o 404 do provider inexistente. `rootPath` inexistente no
    // provider não vira 400 — falha em background e vira notificação ERROR.
    // Disparo repetido para o mesmo (providerId, rootPath) em andamento: 202 sem nova ingestão.
    @Override
    public IngestionStartedResponse ingestProviderSubtree(
            Integer providerId,
            IngestSubtreeRequest ingestSubtreeRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 400: rootPath não existe no provider. 404: provider inexistente.
    // 502: falha do rclone/provider (rcd fora do ar, credenciais inválidas, timeout).
    @Override
    public AvailableFolderNode listAvailableFolders(Integer providerId, String rootPath) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Sempre 204, inclusive com providerId inexistente (sem precheck) ou quando nada casa.
    @Override
    public void removeIngestedSubtree(Integer providerId, String rootPath) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
