package io.github.leonardopinheirolacerda.akari.domain.cache.resource;

import io.github.leonardopinheirolacerda.akari.api.CacheApi;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItemPage;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheQueueRequest;
import io.github.leonardopinheirolacerda.akari.domain.cache.service.CacheService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link CacheApi} — fila de download local de vídeo em {@code /v1/cache/items}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link CacheService}.
 */
@ApplicationScoped
public class CacheResource implements CacheApi {

    @Inject
    CacheService cacheService;

    /**
     * {@code GET /v1/cache/items} — lista as linhas do cache.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param pendingOnly {@code true} restringe às linhas com status diferente de
     *                    {@code COMPLETED}; {@code null}/{@code false} não filtra
     * @return página de linhas ordenadas por ordem de enfileiramento, com {@code 200 OK}
     */
    @Override
    public CacheItemPage listCaches(Integer page, Integer size, Boolean pendingOnly) {
        return cacheService.listCaches(page, size, pendingOnly);
    }

    /**
     * {@code POST /v1/cache/items} — enfileira o download de um arquivo de vídeo remoto.
     *
     * @param cacheQueueRequest id do arquivo de vídeo no catalog a enfileirar
     * @return a linha recém-criada ou reaproveitada, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir no
     *         catalog
     * @throws BusinessRuleException (traduzida em 400) se o arquivo já tiver uma linha
     *         {@code QUEUED}, {@code DOWNLOADING} ou {@code COMPLETED} (uma {@code FAILED} pode
     *         ser reenfileirada), ou se pertencer a um provider {@code LOCAL}
     * @throws IntegrationNotConfiguredException (traduzida em 503) se a preferência
     *         {@code cache.dir} não estiver configurada
     */
    @Override
    public CacheItem queueCacheDownload(CacheQueueRequest cacheQueueRequest) {
        return cacheService.queueCacheDownload(cacheQueueRequest);
    }
}
