package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.Ingestion;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.IngestionTask;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.StorageProviderClient;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.utils.FileNameUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Criação das {@link IngestionTask}s da ingestão: registra os arquivos de vídeo de uma pasta já
 * existente no catalog e a task que representa o processamento dela.
 *
 * <p>Chamado pelo {@code IngestionService} de dentro de threads virtuais, uma por pasta, sem
 * transação ambiente — cada chamada abre a sua.
 */
@ApplicationScoped
public class IngestionTaskService {

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    VideoFileService videoFileService;

    /**
     * Cadastra os vídeos novos da pasta e cria a task dela como {@code PENDING}, numa transação
     * só.
     *
     * <p>Arquivo cujo nome já está cadastrado na pasta é ignorado. A checagem é feita antes de
     * criar, e não pelo {@code ConflictException} do {@code VideoFileService}: a exceção, mesmo
     * capturada, marcaria a transação pra rollback e a task inteira se perderia no commit.
     *
     * @param ingestion ingestão à qual a task pertence, já persistida
     * @param folderId id da pasta, já criada no catalog
     * @param storageVideos arquivos de vídeo diretos da pasta, como listados no provider
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     */
    @Transactional
    public void createIngestionTask(
            Ingestion ingestion,
            Integer folderId,
            List<StorageItem> storageVideos
    ) {
        final MediaFolder mediaFolder = mediaFolderService.findOrThrow(folderId);

        final Set<String> existingNames = videoFileService.listByFolder(folderId)
                .stream()
                .map(videoFile -> videoFile.name)
                .collect(Collectors.toSet());

        for (StorageItem storageVideo : storageVideos) {
            if (existingNames.contains(storageVideo.name())) {
                Log.debugf(
                        "Arquivo já cadastrado, ignorando: %s",
                        storageVideo.name()
                );
                continue;
            }

            createVideoFile(mediaFolder, storageVideo);
        }

        final IngestionTask task = new IngestionTask();
        task.folder = mediaFolder;
        task.ingestion = ingestion;
        task.status = IngestionTaskStatus.PENDING;
        task.createdAt = OffsetDateTime.now();

        IngestionTask.persist(task);
    }

    private void createVideoFile(MediaFolder mediaFolder, StorageItem storageVideo) {
        final VideoFileRequest request = new VideoFileRequest();

        request.setFolderId(mediaFolder.id);
        request.setName(storageVideo.name());
        request.setSizeBytes(storageVideo.size());
        request.format(FileNameUtils.extensionOf(storageVideo.name()));

        videoFileService.createVideoFile(request);
    }
}
