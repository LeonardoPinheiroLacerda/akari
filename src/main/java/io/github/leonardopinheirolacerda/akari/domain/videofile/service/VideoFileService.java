package io.github.leonardopinheirolacerda.akari.domain.videofile.service;

import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.github.leonardopinheirolacerda.akari.domain.videofile.mapper.VideoFileMapper;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class VideoFileService {

    @Inject
    VideoFileMapper videoFileMapper;

    @Inject
    MediaFolderService mediaFolderService;

    @Transactional
    public VideoFileResponse createVideoFile(VideoFileRequest videoFileRequest) {
        Log.infof("Criando arquivo na pasta %d: %s", videoFileRequest.getFolderId(), videoFileRequest.getName());

        final MediaFolder folder = mediaFolderService.findOrThrow(videoFileRequest.getFolderId());

        final VideoFile videoFile = new VideoFile();

        videoFile.folder = folder;
        videoFile.name = videoFileRequest.getName();
        videoFile.path = DirectoryUtils.join(folder.path, videoFileRequest.getName());
        videoFile.sizeBytes = videoFileRequest.getSizeBytes();
        videoFile.format = videoFileRequest.getFormat();

        videoFile.persist();

        Log.infof("Arquivo %d criado com sucesso", videoFile.id);

        return videoFileMapper.toResponse(videoFile);
    }

    @Transactional
    public VideoFileResponse updateVideoFile(Integer fileId, VideoFileRequest videoFileRequest) {
        Log.infof("Atualizando arquivo %d", fileId);

        final VideoFile videoFile = findOrThrow(fileId);

        final MediaFolder folder = mediaFolderService.findOrThrow(videoFileRequest.getFolderId());

        videoFile.folder = folder;
        videoFile.name = videoFileRequest.getName();
        videoFile.path = DirectoryUtils.join(folder.path, videoFileRequest.getName());
        videoFile.sizeBytes = videoFileRequest.getSizeBytes();
        videoFile.format = videoFileRequest.getFormat();

        Log.infof("Arquivo %d atualizado com sucesso", fileId);

        return videoFileMapper.toResponse(videoFile);
    }

    @Transactional
    public void deleteVideoFile(Integer fileId) {
        Log.infof("Removendo arquivo %d", fileId);

        final VideoFile videoFile = findOrThrow(fileId);

        videoFile.delete();

        Log.infof("Arquivo %d removido com sucesso", fileId);
    }

    public VideoFileResponse getVideoFile(Integer fileId) {
        final VideoFile videoFile = findOrThrow(fileId);

        return videoFileMapper.toResponse(videoFile);
    }

    public VideoFilePage listVideoFiles(Integer folderId, Integer page, Integer size) {
        final PageResult<VideoFile> pageResult = VideoFile.findPage(folderId, page, size);

        Log.infof(
                "Listados %d arquivos da pasta %d (página %d, tamanho %d)",
                pageResult.data().size(),
                folderId,
                page,
                size
        );

        return videoFileMapper.toPage(pageResult);
    }

    public VideoFile findOrThrow(Integer fileId) {
        return VideoFile.find(fileId)
                .orElseThrow(() -> {
                    Log.warnf("Arquivo %d não encontrado", fileId);
                    return new ResourceNotFoundException("Não foi possível localizar um arquivo com o id informado");
                });
    }

}
