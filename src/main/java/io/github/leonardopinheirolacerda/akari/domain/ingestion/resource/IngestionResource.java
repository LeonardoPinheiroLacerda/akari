package io.github.leonardopinheirolacerda.akari.domain.ingestion.resource;

import io.github.leonardopinheirolacerda.akari.api.IngestionApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AvailableFolderNode;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestSubtreeRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.IngestionStartedResponse;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.service.IngestionService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link IngestionApi} — ingestão de subtrees de storage provider no catalog em
 * {@code /v1/ingestion}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda a orquestração (validação, criação das
 * pastas, disparo das tasks em background) fica no {@code IngestionService}.
 */
public class IngestionResource implements IngestionApi {

    @Inject
    IngestionService ingestionService;

    /**
     * {@code POST /v1/ingestion/providers/{providerId}} — dispara a ingestão de um subtree do
     * provider.
     *
     * <p>Síncronos: validação (payload, TMDB configurado, provider existente), listagem da
     * árvore remota e criação das pastas no catalog. Em background, uma thread virtual por
     * pasta cadastra os arquivos de vídeo e cria a {@code IngestionTask} da pasta. Disparo
     * repetido sobre o mesmo subtree não é deduplicado.
     *
     * @param providerId id do storage provider a ingerir
     * @param ingestSubtreeRequest subtree (rootPath) e tipo de mídia a ingerir
     * @return status {@code STARTED}, com {@code 202 Accepted}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code providerId} não existir
     * @throws BusinessRuleException (traduzida em 400) se o TMDB não estiver configurado
     *         (preferência {@code tmdb.api-key})
     */
    @Override
    public IngestionStartedResponse ingestProviderSubtree(
            Integer providerId,
            IngestSubtreeRequest ingestSubtreeRequest) {
        return ingestionService.createIngestionTasks(providerId, ingestSubtreeRequest);
    }

    /**
     * {@code GET /v1/ingestion/providers/{providerId}/available-folders} — lista a árvore de
     * pastas remotas do provider, marcando quais já foram ingeridas no catalog.
     *
     * @param providerId id do storage provider
     * @param rootPath subtree a listar; vazio ou {@code null} lista a árvore inteira do provider
     * @return a árvore de pastas a partir de {@code rootPath}, com cada nó marcando se já foi
     *         ingerido
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code providerId} não existir
     * @throws BusinessRuleException (traduzida em 400) se {@code rootPath} não existir no
     *         provider
     * @throws IntegrationException (traduzida em 502) se a listagem no provider remoto falhar
     *         (rclone rcd fora do ar, credenciais inválidas, timeout)
     */
    @Override
    public AvailableFolderNode listAvailableFolders(Integer providerId, String rootPath) {
        return ingestionService.listAvailableFolders(providerId, rootPath);
    }

    /**
     * {@code DELETE /v1/ingestion/providers/{providerId}/available-folders} — remove do catalog
     * tudo que foi ingerido sob um subtree do provider.
     *
     * <p>Leva junto o anime vinculado a cada pasta, os arquivos e as {@code IngestionTask}s das
     * pastas. Sempre devolve {@code 204}, mesmo com {@code providerId} inexistente (sem
     * checagem prévia) ou quando nada no catalog corresponde ao subtree.
     *
     * @param providerId id do storage provider
     * @param rootPath subtree a remover; vazio ou {@code null} remove a árvore inteira ingerida
     *                 do provider
     */
    @Override
    public void removeIngestedSubtree(Integer providerId, String rootPath) {
        ingestionService.removeIngestedSubtree(providerId, rootPath);
    }
}
