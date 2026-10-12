package io.github.leonardopinheirolacerda.akari.domain.cache.service;

import io.github.leonardopinheirolacerda.akari.api.dto.CacheStatus;
import io.github.leonardopinheirolacerda.akari.domain.cache.model.VideoFileCache;
import io.github.leonardopinheirolacerda.akari.domain.cache.sse.CacheEvent;
import io.github.leonardopinheirolacerda.akari.domain.cache.sse.CacheEventBroadcaster;
import io.github.leonardopinheirolacerda.akari.domain.setting.service.SettingService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.StorageProviderClientFactory;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.RcloneClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyJobStatusRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneCopyJobStatusResponse;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneDownloadStatsRequest;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos.RcloneDownloadStatsResponse;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Motor da fila de cache de vídeo — reconcilia downloads em andamento, inicia o próximo da fila
 * e aplica a eviction LRU por orçamento de espaço, driven pelo {@code CacheScheduler}.
 *
 * <p>Faceta distinta de {@link CacheService}, que cuida só do enfileiramento/listagem pros
 * resources — aqui fica a orquestração contra o rclone que só o scheduler dispara.
 */
@ApplicationScoped
public class CacheQueueProcessorService {

    @Inject
    SettingService settingService;

    @Inject
    StorageProviderClientFactory storageProviderClientFactory;

    @Inject
    @RestClient
    RcloneClient rcloneClient;

    @Inject
    CacheEventBroadcaster cacheEventBroadcaster;

    /**
     * Reconcilia a linha em download e inicia a próxima da fila, se o slot estiver livre.
     *
     * <p>Dispara {@code IDLE} se a fila transicionar de ter linhas ativas ({@code QUEUED}/
     * {@code DOWNLOADING}) pra nenhuma.
     */
    @Transactional
    public void processQueue() {
        final long before = VideoFileCache.countActive();

        reconcileInProgress();
        startNextIfIdle();

        if (before > 0 && VideoFileCache.countActive() == 0) {
            cacheEventBroadcaster.publishIdleAndClose();
        }
    }

    /**
     * Publica o progresso da linha em download, se houver inscritos no SSE e um download ativo.
     *
     * <p>Não faz nada sem inscritos — evita bater no rclone à toa quando ninguém está
     * acompanhando.
     */
    @Transactional
    public void pollProgress() {
        if (!cacheEventBroadcaster.hasSubscribers()) {
            return;
        }

        final List<VideoFileCache> downloading = VideoFileCache.findDownloading();

        if (downloading.isEmpty()) {
            return;
        }

        final VideoFileCache row = downloading.get(0);

        if (row.jobId == null) {
            return;
        }

        try {
            final RcloneDownloadStatsRequest request = new RcloneDownloadStatsRequest("job/" + row.jobId);
            final RcloneDownloadStatsResponse stats = rcloneClient.getTransferProgress(request);

            final CacheEvent progress = CacheEvent.progress(
                    row.id,
                    stats.bytes(),
                    stats.totalBytes(),
                    stats.speed(),
                    stats.eta(),
                    stats.getPercentage()
            );

            cacheEventBroadcaster.publish(progress);

        } catch (ProcessingException | WebApplicationException e) {
            Log.warnf(e, "Falha ao consultar progresso do job %d", row.jobId);
        }
    }

    private void reconcileInProgress() {
        for (VideoFileCache row : VideoFileCache.findDownloading()) {
            final RcloneCopyJobStatusResponse response;

            try {
                response = rcloneClient.getJobStatus(new RcloneCopyJobStatusRequest(row.jobId));
            } catch (ProcessingException | WebApplicationException e) {
                Log.warnf(e, "Falha ao consultar status do job %d (linha %s) — tentando de novo no próximo ciclo", row.jobId, row.id);
                continue;
            }

            if (!response.finished()) {
                continue;
            }

            if (response.success()) {
                row.status = CacheStatus.COMPLETED;
                row.fetchedAt = OffsetDateTime.now();
                row.jobId = null;

                Log.infof("Download da linha %s concluído", row.id);

                final CacheEvent completed = CacheEvent.completed(row.id);
                cacheEventBroadcaster.publish(completed);

            } else {
                row.status = CacheStatus.FAILED;
                row.jobId = null;

                Log.warnf("Download da linha %s falhou: %s", row.id, response.error());

                final CacheEvent failed = CacheEvent.failed(row.id, response.error());
                cacheEventBroadcaster.publish(failed);
            }
        }
    }

    private void startNextIfIdle() {
        if (!VideoFileCache.findDownloading().isEmpty()) {
            return;
        }

        final Optional<VideoFileCache> next = VideoFileCache.findOldestQueued();

        if (next.isEmpty()) {
            return;
        }

        final VideoFileCache row = next.get();

        final String cacheDir = settingService.getCacheDir();

        if (cacheDir == null || cacheDir.isBlank()) {
            Log.warnf("cache.dir não configurado — adiando início do download da linha %s", row.id);
            return;
        }

        evictForIncoming(row.videoFile.sizeBytes, cacheDir);

        final StorageProvider storageProvider = row.videoFile.folder.storageProvider;
        final StorageProviderClient client = storageProviderClientFactory.getClient(storageProvider);

        final Long jobId;

        try {
            jobId = client.downloadFile(
                    row.videoFile.path,
                    cacheDir,
                    row.id.toString()
            );

        } catch (ProcessingException | WebApplicationException e) {
            Log.errorf(e, "Falha ao iniciar download rclone da linha %s", row.id);

            row.status = CacheStatus.FAILED;

            final CacheEvent failed = CacheEvent.failed(row.id, "Falha ao iniciar o download no rclone");
            cacheEventBroadcaster.publish(failed);

            return;
        }

        row.status = CacheStatus.DOWNLOADING;
        row.jobId = jobId;

        Log.infof("Download da linha %s iniciado (job rclone %d)", row.id, jobId);

        cacheEventBroadcaster.publish(CacheEvent.started(row.id));
    }

    private void evictForIncoming(Long incomingSizeBytes, String cacheDir) {
        final long incoming = incomingSizeBytes != null
                ? incomingSizeBytes
                : 0L;

        final long maxBytes = settingService.getCacheMaxSizeBytes();

        final List<VideoFileCache> completedOrderByFetchedAtAsc = VideoFileCache.findCompletedOrderByFetchedAtAsc();
        final LinkedList<VideoFileCache> completed = new LinkedList<>(completedOrderByFetchedAtAsc);

        long currentBytes = completed.stream()
                .mapToLong(c -> c.videoFile.sizeBytes != null ? c.videoFile.sizeBytes : 0L)
                .sum();

        while (currentBytes + incoming > maxBytes && !completed.isEmpty()) {
            final VideoFileCache victim = completed.removeFirst();
            final long victimBytes = victim.videoFile.sizeBytes != null
                    ? victim.videoFile.sizeBytes
                    : 0L;

            final UUID victimId = victim.id;

            deleteOnDisk(cacheDir, victimId);
            victim.delete();

            currentBytes -= victimBytes;

            Log.infof("Linha %s removida do cache por eviction LRU", victimId);

            final CacheEvent evicted = CacheEvent.evicted(victimId);
            cacheEventBroadcaster.publish(evicted);
        }

        if (currentBytes + incoming > maxBytes) {
            Log.warnf("Arquivo de %d bytes sozinho excede o orçamento de cache (%d bytes) — prosseguindo mesmo assim", incoming, maxBytes);
        }
    }

    private void deleteOnDisk(String cacheDir, UUID id) {
        try {
            Files.deleteIfExists(Path.of(cacheDir, id.toString()));
        } catch (IOException e) {
            Log.warnf(e, "Falha ao apagar arquivo em disco do cache %s — removendo a linha mesmo assim", id);
        }
    }

}
