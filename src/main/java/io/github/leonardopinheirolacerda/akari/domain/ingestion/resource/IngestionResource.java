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
 * Implementa {@link IngestionApi} — reconciliação subtree ↔ catalog em {@code /v1/ingestion}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda a orquestração (validação síncrona,
 * disparo da reconciliação em background) fica no {@code IngestionService}.
 */
public class IngestionResource implements IngestionApi {

    @Inject
    IngestionService ingestionService;

    /**
     * {@code POST /v1/ingestion/providers/{providerId}} — dispara a ingestão de um subtree do
     * provider.
     *
     * <p>Só a validação inicial é síncrona (payload, TMDB configurado, provider existente);
     * o percurso do provider e a reconciliação em si rodam em background numa thread virtual.
     * {@code rootPath} inexistente no provider não vira erro síncrono — a reconciliação falha
     * em background e vira uma notificação {@code ERROR}. Disparo repetido para o mesmo
     * {@code (providerId, rootPath)} já em andamento devolve o mesmo {@code STARTED} sem
     * iniciar uma nova reconciliação.
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
        return ingestionService.ingestProviderSubtree(providerId, ingestSubtreeRequest);
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
     * <p>Sempre devolve {@code 204}, mesmo com {@code providerId} inexistente (sem checagem
     * prévia) ou quando nada no catalog corresponde ao subtree.
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
