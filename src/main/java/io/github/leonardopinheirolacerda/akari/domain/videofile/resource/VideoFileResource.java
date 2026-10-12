package io.github.leonardopinheirolacerda.akari.domain.videofile.resource;

import io.github.leonardopinheirolacerda.akari.api.VideoFileApi;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link VideoFileApi} — arquivos de vídeo do catalog em
 * {@code /v1/catalog/video-files}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no
 * {@link VideoFileService}.
 */
@ApplicationScoped
public class VideoFileResource implements VideoFileApi {

    @Inject
    VideoFileService videoFileService;

    /**
     * {@code POST /v1/catalog/video-files} — cria um novo arquivo.
     *
     * <p>O {@code path} é derivado server-side a partir da pasta pai + {@code name}.
     *
     * @param videoFileRequest dados do arquivo a criar
     * @return o arquivo recém-criado, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code folderId} não existir
     */
    @Override
    public VideoFileResponse createVideoFile(VideoFileRequest videoFileRequest) {
        return videoFileService.createVideoFile(videoFileRequest);
    }

    /**
     * {@code DELETE /v1/catalog/video-files/{fileId}} — remove o arquivo.
     *
     * @param fileId id do arquivo a remover
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir
     */
    @Override
    public void deleteVideoFile(Integer fileId) {
        videoFileService.deleteVideoFile(fileId);
    }

    /**
     * {@code GET /v1/catalog/video-files/{fileId}} — busca um arquivo por id.
     *
     * @param fileId id do arquivo
     * @return o arquivo, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} não existir
     */
    @Override
    public VideoFileResponse getVideoFile(Integer fileId) {
        return videoFileService.getVideoFile(fileId);
    }

    /**
     * {@code GET /v1/catalog/video-files} — lista paginada dos arquivos de uma pasta.
     *
     * <p>Se a pasta não existir ou não tiver arquivos, devolve página vazia (não 404).
     *
     * @param folderId id da pasta a listar
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de arquivos da pasta, com {@code 200 OK}
     */
    @Override
    public VideoFilePage listVideoFiles(Integer folderId, Integer page, Integer size) {
        return videoFileService.listVideoFiles(folderId, page, size);
    }

    /**
     * {@code PUT /v1/catalog/video-files/{fileId}} — substitui os campos mutáveis do arquivo.
     *
     * @param fileId id do arquivo a atualizar
     * @param videoFileRequest novos dados do arquivo
     * @return o arquivo atualizado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code fileId} ou o novo
     *         {@code folderId} não existirem
     */
    @Override
    public VideoFileResponse updateVideoFile(Integer fileId, VideoFileRequest videoFileRequest) {
        return videoFileService.updateVideoFile(fileId, videoFileRequest);
    }
}
