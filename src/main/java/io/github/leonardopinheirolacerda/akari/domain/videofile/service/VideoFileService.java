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
import java.util.List;

/**
 * Arquivos de vídeo do catalog: CRUD administrado pela API e os métodos de suporte usados pela
 * ingestão (listagem sem paginação pra reconciliação em lote).
 */
@ApplicationScoped
public class VideoFileService {

    @Inject
    VideoFileMapper videoFileMapper;

    @Inject
    MediaFolderService mediaFolderService;

    /**
     * Cria um novo arquivo na pasta informada, derivando o {@code path} a partir da pasta +
     * {@code name}.
     *
     * @param videoFileRequest dados do arquivo a criar
     * @return o arquivo recém-criado
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     */
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

    /**
     * Substitui os campos mutáveis de um arquivo existente, inclusive a pasta.
     *
     * @param fileId id do arquivo a atualizar
     * @param videoFileRequest novos dados do arquivo
     * @return o arquivo atualizado
     * @throws ResourceNotFoundException se o {@code fileId} ou o novo {@code folderId} não
     *         existirem
     */
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

    /**
     * Remove um arquivo.
     *
     * @param fileId id do arquivo a remover
     * @throws ResourceNotFoundException se o {@code fileId} não existir
     */
    @Transactional
    public void deleteVideoFile(Integer fileId) {
        Log.infof("Removendo arquivo %d", fileId);

        final VideoFile videoFile = findOrThrow(fileId);

        videoFile.delete();

        Log.infof("Arquivo %d removido com sucesso", fileId);
    }

    /**
     * Busca um arquivo por id.
     *
     * @param fileId id do arquivo
     * @return o arquivo
     * @throws ResourceNotFoundException se o {@code fileId} não existir
     */
    public VideoFileResponse getVideoFile(Integer fileId) {
        final VideoFile videoFile = findOrThrow(fileId);

        return videoFileMapper.toResponse(videoFile);
    }

    /**
     * Pagina os arquivos de uma pasta.
     *
     * @param folderId id da pasta a listar
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de arquivos da pasta; vazia se a pasta não existir ou não tiver arquivos
     */
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

    /**
     * Lista todos os arquivos de uma pasta, sem paginação. Usado pela ingestão (reconciliação e
     * pairing automático) de contextos sem transação ambiente — por isso é {@code @Transactional}
     * mesmo sendo leitura.
     *
     * @param folderId id da pasta no catalog
     * @return os arquivos da pasta
     */
    @Transactional
    public List<VideoFile> listByFolder(Integer folderId) {
        return VideoFile.findByFolderId(folderId);
    }

    /**
     * Busca um arquivo por id, ou lança se não existir.
     *
     * @param fileId id do arquivo
     * @return o arquivo
     * @throws ResourceNotFoundException se o {@code fileId} não existir
     */
    public VideoFile findOrThrow(Integer fileId) {
        return VideoFile.find(fileId)
                .orElseThrow(() -> {
                    Log.warnf("Arquivo %d não encontrado", fileId);
                    return new ResourceNotFoundException("Não foi possível localizar um arquivo com o id informado");
                });
    }

}
