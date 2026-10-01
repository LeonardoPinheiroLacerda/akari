package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.MediaFoldersApi;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class MediaFoldersResource implements MediaFoldersApi {

    // Pai não registrado NÃO é erro: a pasta vira raiz (parentId null), sem 404.
    @Override
    public MediaFolderResponse createFolder(MediaFolderRequest mediaFolderRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void deleteFolder(Integer folderId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public MediaFolderResponse getFolder(Integer folderId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
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
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public MediaFolderResponse updateFolder(
            Integer folderId,
            MediaFolderRequest mediaFolderRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
