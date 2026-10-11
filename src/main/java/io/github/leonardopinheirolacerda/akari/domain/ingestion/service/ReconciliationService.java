package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.FolderReconciliationResult;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.model.StorageItem;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.utils.FileNameUtils;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sincroniza o catalog (`MediaFolder`/`VideoFile`) com o que existe de fato no storage provider
 * — os Steps 2-3 da reconciliação (ver {@link IngestionService}).
 *
 * <p>Classe separada de {@link IngestionService} de propósito: cada método aqui é sua própria
 * transação, chamada em sequência pela thread virtual da ingestão (sem transação JTA ambiente,
 * diferente de uma request HTTP). Sendo um bean à parte, a chamada sai pelo proxy do CDI
 * naturalmente — sem precisar do contorno de auto-injeção que seria necessário se esses métodos
 * estivessem na mesma classe de quem os chama em loop.
 */
@ApplicationScoped
public class ReconciliationService {

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    VideoFileService videoFileService;

    @Inject
    FolderRemovalService folderRemovalService;

    /**
     * Step 2 — remove do catalog pastas/arquivos do subtree que não existem mais no provider.
     * Uma transação só, sem I/O externo.
     */
    @Transactional
    public void reconcileRemovals(Integer providerId,
                                  String rootPath,
                                  List<StorageItem> remoteDirs,
                                  List<StorageItem> remoteVideos) {

        final Set<String> remoteDirPaths = remoteDirs.stream()
                .map(StorageItem::path)
                .collect(Collectors.toSet());

        final Set<String> remoteVideoPaths = remoteVideos.stream()
                .map(StorageItem::path)
                .collect(Collectors.toSet());

        final List<MediaFolder> existingFolders = mediaFolderService.findSubtree(providerId, rootPath);
        final List<MediaFolder> staleFolders = existingFolders.stream()
                .filter(folder -> !remoteDirPaths.contains(folder.path))
                .toList();

        folderRemovalService.removeFolders(staleFolders);

        final Set<Integer> staleFolderIds = staleFolders.stream()
                .map(folder -> folder.id)
                .collect(Collectors.toSet());

        for (MediaFolder folder : existingFolders) {
            if (staleFolderIds.contains(folder.id)) {
                continue;
            }

            for (VideoFile videoFile : videoFileService.listByFolder(folder.id)) {
                if (!remoteVideoPaths.contains(videoFile.path)) {
                    videoFileService.deleteVideoFile(videoFile.id);
                }
            }
        }
    }

    /**
     * Step 3 — cria (ou reencontra) uma pasta e os arquivos de vídeo diretos dela. Chamado uma
     * vez por diretório remoto, em ordem de profundidade ascendente — cada chamada é sua
     * própria transação, por isso o pai sempre existe no catalog antes do filho ser processado.
     */
    @Transactional
    public FolderReconciliationResult reconcileFolder(Integer providerId,
                                                      StorageItem dir,
                                                      MediaType mediaType,
                                                      List<StorageItem> directVideos) {

        boolean created = false;
        MediaFolder folder = mediaFolderService.findByProviderAndPath(providerId, dir.path())
                .orElse(null);

        if (folder == null) {
            folder = mediaFolderService.create(providerId, dir.path(), dir.name(), mediaType);
            created = true;
        }

        final Set<String> existingFilePaths = videoFileService.listByFolder(folder.id).stream()
                .map(file -> file.path)
                .collect(Collectors.toSet());

        for (StorageItem videoItem : directVideos) {
            if (existingFilePaths.contains(videoItem.path())) {
                continue;
            }

            videoFileService.createVideoFile(new VideoFileRequest()
                    .folderId(folder.id)
                    .name(videoItem.name())
                    .sizeBytes(videoItem.size())
                    .format(FileNameUtils.extensionOf(videoItem.name())));
        }

        return new FolderReconciliationResult(folder.id, folder.name, created, !directVideos.isEmpty());
    }

}
