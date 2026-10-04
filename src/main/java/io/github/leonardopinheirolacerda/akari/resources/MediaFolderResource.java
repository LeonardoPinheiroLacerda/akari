package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.MediaFolderApi;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.services.MediaFolderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

@ApplicationScoped
public class MediaFolderResource implements MediaFolderApi {

    @Inject
    MediaFolderService service;

    // Pai não registrado NÃO é erro: a pasta vira raiz (parentId null), sem 404.
    @Override
    public MediaFolderResponse createFolder(MediaFolderRequest mediaFolderRequest) {
        return service.createFolder(mediaFolderRequest);
    }

    @Override
    public void deleteFolder(Integer folderId) {
        service.deleteFolder(folderId);
    }

    @Override
    public MediaFolderResponse getFolder(Integer folderId) {
        return service.getFolder(folderId);
    }

    @Override
    public MediaFolderPage listFolders(
            Integer page,
            Integer size,
            String name,
            MediaType mediaType,
            Integer storageProviderId,
            Integer parentId,
            Boolean hasFiles,
            List<String> sort) {
        return service.listFolders(
                page,
                size,
                name,
                mediaType,
                storageProviderId,
                parentId,
                hasFiles,
                sort
        );
    }

    @Override
    public MediaFolderResponse updateFolder(Integer folderId, MediaFolderRequest mediaFolderRequest) {
        return service.updateFolder(folderId, mediaFolderRequest);
    }
}
