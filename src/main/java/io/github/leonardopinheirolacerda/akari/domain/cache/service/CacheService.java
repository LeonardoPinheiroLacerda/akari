package io.github.leonardopinheirolacerda.akari.domain.cache.service;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheItem;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheItemPage;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheQueueRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.StorageProviderType;
import io.github.leonardopinheirolacerda.akari.domain.cache.mapper.CacheMapper;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.VideoFileCache;
import io.github.leonardopinheirolacerda.akari.domain.cache.sse.CacheEvent;
import io.github.leonardopinheirolacerda.akari.domain.cache.sse.CacheEventBroadcaster;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.mapper.VideoFileMapper;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Enfileiramento e listagem do cache de vídeo — a face do domínio voltada pro
 * {@code CacheResource} e pro snapshot inicial do {@code CacheEventResource}.
 *
 * <p>O motor que efetivamente processa a fila (download via rclone, reconciliação, eviction
 * LRU), consumido pelo {@code CacheScheduler}, mora em {@link CacheQueueProcessorService} —
 * facetas distintas do mesmo {@link VideoFileCache}, como o skill de code style recomenda pra
 * não inflar um único service com responsabilidades não relacionadas.
 */
@ApplicationScoped
public class CacheService {

    @Inject
    CacheMapper cacheMapper;

    @Inject
    VideoFileMapper videoFileMapper;

    @Inject
    SettingService settingService;

    @Inject
    CacheEventBroadcaster cacheEventBroadcaster;

    /**
     * Enfileira o download de um arquivo de vídeo remoto pro cache local.
     *
     * <p>Cria uma linha {@code QUEUED} nova, ou reaproveita (mesmo {@code id}) uma linha
     * {@code FAILED} existente. O download em si acontece depois, dirigido pelo
     * {@code CacheScheduler}/{@link CacheQueueProcessorService}. Publica o evento SSE
     * {@code QUEUED} ao final.
     *
     * @param cacheQueueRequest id do arquivo de vídeo no catalog a enfileirar
     * @return a linha recém-criada ou reaproveitada, com status {@code QUEUED}
     * @throws ResourceNotFoundException se o {@code fileId} não existir no catalog
     * @throws BusinessRuleException se o arquivo já tiver uma linha {@code QUEUED},
     *         {@code DOWNLOADING} ou {@code COMPLETED} (uma {@code FAILED} pode ser
     *         reenfileirada), ou se pertencer a um provider {@code LOCAL} (lido direto do
     *         disco, sem cache)
     * @throws IntegrationNotConfiguredException se a preferência {@code cache.dir} não estiver
     *         configurada
     */
    @Transactional
    public CacheItem queueCacheDownload(CacheQueueRequest cacheQueueRequest) {
        final VideoFile videoFile = VideoFile.find(cacheQueueRequest.getFileId())
                .orElseThrow(() -> {
                    Log.warnf("Arquivo de vídeo %d não encontrado pra enfileirar no cache", cacheQueueRequest.getFileId());
                    return new ResourceNotFoundException("Não foi possível localizar um arquivo de vídeo com o id informado");
                });

        final Optional<VideoFileCache> existing = VideoFileCache.findByVideoFileId(videoFile.id);

        if (existing.isPresent() && existing.get().status != CacheStatus.FAILED) {
            throw new BusinessRuleException("Este arquivo já possui uma linha de cache ativa (QUEUED, DOWNLOADING ou COMPLETED)");
        }

        if (videoFile.folder.storageProvider.type == StorageProviderType.LOCAL) {
            throw new BusinessRuleException("Arquivos de um provider LOCAL são lidos direto do disco e não usam cache");
        }

        final String cacheDir = settingService.getCacheDir();

        if (cacheDir == null || cacheDir.isBlank()) {
            throw new IntegrationNotConfiguredException(
                    "Cache local não configurado. Cadastre o diretório na preferência 'cache.dir' pra habilitar o download.");
        }

        final VideoFileCache row = existing.orElseGet(() -> {
            final VideoFileCache created = new VideoFileCache();
            created.id = UUID.randomUUID();
            created.videoFile = videoFile;
            return created;
        });

        row.status = CacheStatus.QUEUED;
        row.jobId = null;
        row.queuedAt = OffsetDateTime.now();

        row.persist();

        Log.infof("Arquivo %d enfileirado no cache (linha %s)", videoFile.id, row.id);

        final CacheEvent queued = CacheEvent.queued(row.id);
        cacheEventBroadcaster.publish(queued);

        return cacheMapper.toItem(row, videoFileMapper);
    }

    /**
     * Pagina todas as linhas do cache, na ordem em que entraram na fila.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de linhas, com o arquivo de vídeo resolvido em cada uma
     */
    public CacheItemPage listCaches(Integer page, Integer size) {
        final PageResult<VideoFileCache> data = VideoFileCache.findPage(page, size);
        return cacheMapper.toPage(data, videoFileMapper);
    }

    /**
     * Pagina as linhas ainda não concluídas, na ordem em que entraram na fila.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de linhas com status diferente de {@code COMPLETED} (inclui
     *         {@code FAILED}, já que pode ser reenfileirada)
     */
    public CacheItemPage listPendingCaches(Integer page, Integer size) {
        final PageResult<VideoFileCache> data = VideoFileCache.findPendingPage(page, size);
        return cacheMapper.toPage(data, videoFileMapper);
    }

    /**
     * Lista as linhas ainda não resolvidas, pro snapshot inicial de um cliente SSE.
     *
     * @return as linhas {@code QUEUED} ou {@code DOWNLOADING}, com o arquivo de vídeo resolvido
     */
    public List<CacheItem> listActiveCaches() {
        return VideoFileCache.findActive()
                .stream()
                .map(row -> cacheMapper.toItem(row, videoFileMapper))
                .toList();
    }

}
