package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.VideoFilesApi;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class VideoFilesResource implements VideoFilesApi {

    // 404: folderId inexistente.
    @Override
    public VideoFileResponse createVideoFile(VideoFileRequest videoFileRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void deleteVideoFile(Integer fileId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public VideoFileResponse getVideoFile(Integer fileId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Pasta inexistente ou sem arquivos: página vazia, não 404.
    @Override
    public VideoFilePage listVideoFiles(Integer folderId, Integer page, Integer size) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // 404: fileId ou folderId inexistente.
    @Override
    public VideoFileResponse updateVideoFile(Integer fileId, VideoFileRequest videoFileRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
