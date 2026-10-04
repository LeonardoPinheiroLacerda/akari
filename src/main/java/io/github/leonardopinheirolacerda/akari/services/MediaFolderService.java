package io.github.leonardopinheirolacerda.akari.services;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.mapper.MediaFolderMapper;
import io.github.leonardopinheirolacerda.akari.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.model.pageable.PageResult;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class MediaFolderService {

    @Inject
    MediaFolderMapper mapper;

    @Inject
    StorageProvidersService providersService;


    @Transactional
    public MediaFolderResponse createFolder(MediaFolderRequest mediaFolderRequest) {
        Log.infof(
                "Criando pasta no provider %d: %s",
                mediaFolderRequest.getStorageProviderId(),
                mediaFolderRequest.getPath()
        );

        final StorageProvider storageProvider = providersService
                .findOrThrow(mediaFolderRequest.getStorageProviderId());

        final Optional<MediaFolder> existingFolder = MediaFolder.findByProviderAndPath(
                mediaFolderRequest.getStorageProviderId(),
                mediaFolderRequest.getPath()
        );

        if(existingFolder.isPresent()) {
            Log.warnf(
                    "Já existe uma pasta no provider %d com o caminho %s",
                    mediaFolderRequest.getStorageProviderId(),
                    mediaFolderRequest.getPath()
            );
            throw new BusinessRuleException("Já existe uma pasta salva com esse caminho");
        }

        final String parentPath = DirectoryUtils.parentOf(mediaFolderRequest.getPath());

        final MediaFolder parent = MediaFolder.findByProviderAndPath(storageProvider.id, parentPath)
                .orElse(null);

        final MediaFolder folder = new MediaFolder();

        folder.mediaType = mediaFolderRequest.getMediaType();
        folder.name = mediaFolderRequest.getName();
        folder.path = mediaFolderRequest.getPath();
        folder.parent = parent;
        folder.storageProvider = storageProvider;

        folder.persist();

        Log.infof("Pasta %d criada com sucesso", folder.id);

        return mapper.toResponse(folder);

    }

    @Transactional
    public MediaFolderResponse updateFolder(Integer folderId, MediaFolderRequest mediaFolderRequest) {
        Log.infof("Atualizando pasta %d", folderId);

        final MediaFolder folder = findOrThrow(folderId);

        final Optional<MediaFolder> existingFolder = MediaFolder.findByProviderAndPath(
                mediaFolderRequest.getStorageProviderId(),
                mediaFolderRequest.getPath()
        );

        if (existingFolder.isPresent() && !existingFolder.get().id.equals(folderId)) {
            Log.warnf(
                    "Pasta %d não pode ser atualizada: já existe outra pasta no provider %d com o caminho %s",
                    folderId,
                    mediaFolderRequest.getStorageProviderId(),
                    mediaFolderRequest.getPath()
            );
            throw new BusinessRuleException("Já existe uma pasta salva com esse caminho");
        }

        final StorageProvider storageProvider = providersService
                .findOrThrow(mediaFolderRequest.getStorageProviderId());

        final String parentPath = DirectoryUtils.parentOf(mediaFolderRequest.getPath());

        final MediaFolder parent = MediaFolder.findByProviderAndPath(storageProvider.id, parentPath)
                .orElse(null);

        folder.mediaType = mediaFolderRequest.getMediaType();
        folder.name = mediaFolderRequest.getName();
        folder.path = mediaFolderRequest.getPath();
        folder.parent = parent;
        folder.storageProvider = storageProvider;

        Log.infof("Pasta %d atualizada com sucesso", folderId);

        return mapper.toResponse(folder);
    }

    @Transactional
    public void deleteFolder(Integer folderId) {
        Log.infof("Removendo pasta %d", folderId);

        final MediaFolder folder = findOrThrow(folderId);

        folder.delete();

        Log.infof("Pasta %d removida com sucesso", folderId);
    }

    public MediaFolderResponse getFolder(Integer folderId) {
        final MediaFolder folder = findOrThrow(folderId);

        return mapper.toResponse(folder);
    }

    public MediaFolderPage listFolders(
            Integer page,
            Integer size,
            String name,
            MediaType mediaType,
            Integer storageProviderId,
            Integer parentId,
            Boolean hasFiles,
            List<String> sort) {

        final PageResult<MediaFolder> pageResult = MediaFolder.findPage(
                page,
                size,
                name,
                mediaType,
                storageProviderId,
                parentId,
                hasFiles,
                sort
        );

        Log.infof("Listadas %d pastas (página %d, tamanho %d)", pageResult.data().size(), page, size);

        return mapper.toPage(pageResult);
    }

    public MediaFolder findOrThrow(Integer folderId) {
        return MediaFolder.find(folderId)
                .orElseThrow(() -> {
                    Log.warnf("Pasta %d não encontrada", folderId);
                    return new ResourceNotFoundException("Não foi possível localizar uma pasta com o id informado");
                });
    }

}
