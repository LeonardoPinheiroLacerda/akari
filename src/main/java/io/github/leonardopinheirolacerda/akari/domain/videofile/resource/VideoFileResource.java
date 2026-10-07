package io.github.leonardopinheirolacerda.akari.domain.videofile.resource;

import io.github.leonardopinheirolacerda.akari.api.VideoFileApi;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VideoFileResource implements VideoFileApi {

    @Inject
    VideoFileService videoFileService;

    // 404: folderId inexistente.
    @Override
    public VideoFileResponse createVideoFile(VideoFileRequest videoFileRequest) {
        return videoFileService.createVideoFile(videoFileRequest);
    }

    @Override
    public void deleteVideoFile(Integer fileId) {
        videoFileService.deleteVideoFile(fileId);
    }

    @Override
    public VideoFileResponse getVideoFile(Integer fileId) {
        return videoFileService.getVideoFile(fileId);
    }

    // Pasta inexistente ou sem arquivos: página vazia, não 404.
    @Override
    public VideoFilePage listVideoFiles(Integer folderId, Integer page, Integer size) {
        return videoFileService.listVideoFiles(folderId, page, size);
    }

    // 404: fileId ou folderId inexistente.
    @Override
    public VideoFileResponse updateVideoFile(Integer fileId, VideoFileRequest videoFileRequest) {
        return videoFileService.updateVideoFile(fileId, videoFileRequest);
    }
}
