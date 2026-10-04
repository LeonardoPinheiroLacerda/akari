package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.VideoFileApi;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.services.VideoFileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VideoFileResource implements VideoFileApi {

    @Inject
    VideoFileService service;

    // 404: folderId inexistente.
    @Override
    public VideoFileResponse createVideoFile(VideoFileRequest videoFileRequest) {
        return service.createVideoFile(videoFileRequest);
    }

    @Override
    public void deleteVideoFile(Integer fileId) {
        service.deleteVideoFile(fileId);
    }

    @Override
    public VideoFileResponse getVideoFile(Integer fileId) {
        return service.getVideoFile(fileId);
    }

    // Pasta inexistente ou sem arquivos: página vazia, não 404.
    @Override
    public VideoFilePage listVideoFiles(Integer folderId, Integer page, Integer size) {
        return service.listVideoFiles(folderId, page, size);
    }

    // 404: fileId ou folderId inexistente.
    @Override
    public VideoFileResponse updateVideoFile(Integer fileId, VideoFileRequest videoFileRequest) {
        return service.updateVideoFile(fileId, videoFileRequest);
    }
}
