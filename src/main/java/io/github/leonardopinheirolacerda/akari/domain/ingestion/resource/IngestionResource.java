package io.github.leonardopinheirolacerda.akari.domain.ingestion.resource;

import io.github.leonardopinheirolacerda.akari.api.IngestionApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AvailableFolderNode;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestSubtreeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStartedResponse;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionService;
import jakarta.inject.Inject;

public class IngestionResource implements IngestionApi {

    @Inject
    IngestionService ingestionService;

    // Síncronos: 400 do payload, 400 de TMDB não configurado (precheck de tmdb.api-key), e 404
    // do provider inexistente. `rootPath` inexistente no provider não vira 400 — falha em
    // background e vira notificação ERROR.
    // Disparo repetido para o mesmo (providerId, rootPath) em andamento: 202 sem nova ingestão.
    @Override
    public IngestionStartedResponse ingestProviderSubtree(
            Integer providerId,
            IngestSubtreeRequest ingestSubtreeRequest) {
        return ingestionService.ingestProviderSubtree(providerId, ingestSubtreeRequest);
    }

    // 400: rootPath não existe no provider. 404: provider inexistente.
    // 502: falha do rclone/provider (rcd fora do ar, credenciais inválidas, timeout).
    @Override
    public AvailableFolderNode listAvailableFolders(Integer providerId, String rootPath) {
        return ingestionService.listAvailableFolders(providerId, rootPath);
    }

    // Sempre 204, inclusive com providerId inexistente (sem precheck) ou quando nada casa.
    @Override
    public void removeIngestedSubtree(Integer providerId, String rootPath) {
        ingestionService.removeIngestedSubtree(providerId, rootPath);
    }
}
