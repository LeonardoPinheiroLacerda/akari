package io.github.leonardopinheirolacerda.akari.domain.videofile.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFilePage;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.VideoFileResponse;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbFilePairingService;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
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
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arquivos de vídeo do catalog: CRUD administrado pela API e os métodos de suporte usados pela
 * ingestão (listagem sem paginação pra reconciliação em lote).
 */
@ApplicationScoped
public class VideoFileService {

    private static final int EPISODE_NUMBER_PADDING_WIDTH = 20;

    @Inject
    VideoFileMapper videoFileMapper;

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    AnimeTmdbFilePairingService animeTmdbFilePairingService;

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
        videoFile.naturalSortKey = naturalSortKeyOf(videoFile.name);

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
        videoFile.naturalSortKey = naturalSortKeyOf(videoFile.name);

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
     * Pagina os arquivos de uma pasta, já com o pairing TMDB de cada um embutido — evita o
     * cliente paginar e depois buscar os pairings em lote numa segunda chamada.
     *
     * @param folderId id da pasta a listar
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de arquivos da pasta; vazia se a pasta não existir ou não tiver arquivos;
     *         cada arquivo com {@code tmdbPairing} preenchido quando pareado
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

        return toPageWithPairings(pageResult);
    }

    /**
     * Pagina os arquivos de uma pasta em ordem natural de {@code name}, já com {@code
     * tmdbPairing} embutido — base da listagem de episódios do anime, onde a posição vem do
     * nome do arquivo, não do pareamento.
     *
     * @param folderId id da pasta a listar
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return página de arquivos da pasta em ordem natural; vazia se a pasta não existir ou não
     *         tiver arquivos; cada arquivo com {@code tmdbPairing} preenchido quando pareado
     */
    public VideoFilePage listVideoFilesOrderedByNaturalSortKey(Integer folderId, Integer page, Integer size) {
        final PageResult<VideoFile> pageResult = VideoFile.findPageOrderedByNaturalSortKey(folderId, page, size);

        Log.infof(
                "Listados %d arquivos da pasta %d em ordem natural (página %d, tamanho %d)",
                pageResult.data().size(),
                folderId,
                page,
                size
        );

        return toPageWithPairings(pageResult);
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
     * Lista todos os arquivos de uma pasta, sem paginação, na ordem natural dos nomes (a mesma
     * da listagem de episódios). Usado pela resolução de pairing de episódios, que roda tanto em
     * request quanto na ingestão (sem transação ambiente) — por isso é {@code @Transactional}
     * mesmo sendo leitura.
     *
     * @param folderId id da pasta no catalog
     * @return os arquivos da pasta, em ordem natural
     */
    @Transactional
    public List<VideoFile> listByFolderOrderedByNaturalSortKey(Integer folderId) {
        return VideoFile.findByFolderIdOrderedByNaturalSortKey(folderId);
    }

    /**
     * Preenche a chave de ordenação natural de um arquivo já catalogado, se ainda não tiver
     * uma — usado pela reconciliação ao reencontrar, numa nova varredura, um arquivo ingerido
     * antes dessa coluna existir. Arquivo que já tem a chave não é recalculado.
     *
     * @param videoFile arquivo já existente no catalog
     */
    @Transactional
    public void backfillNaturalSortKeyIfMissing(VideoFile videoFile) {
        if (videoFile.naturalSortKey != null) {
            return;
        }

        videoFile.naturalSortKey = naturalSortKeyOf(videoFile.name);
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

    /**
     * Mapeia uma página de arquivos pro DTO e embute o pareamento TMDB de cada um, buscado em
     * lote pelos {@code fileId}s da própria página.
     *
     * @param pageResult página de arquivos já ordenada e paginada
     * @return a página mapeada, com {@code tmdbPairing} preenchido quando pareado
     */
    private VideoFilePage toPageWithPairings(PageResult<VideoFile> pageResult) {
        final VideoFilePage filePage = videoFileMapper.toPage(pageResult);

        final List<Integer> fileIds = filePage.getData()
                .stream()
                .map(VideoFileResponse::getId)
                .toList();

        final Map<Integer, TmdbFilePairingResponse> pairingsByFileId = animeTmdbFilePairingService
                .getTmdbFilePairings(fileIds)
                .stream()
                .collect(Collectors.toMap(TmdbFilePairingResponse::getFileId, Function.identity()));

        filePage.getData().forEach(file -> file.tmdbPairing(pairingsByFileId.get(file.getId())));

        return filePage;
    }

    /**
     * Calcula a chave de ordenação natural de um arquivo a partir do número de episódio
     * extraído do nome (mesma heurística do pairing automático, {@link EpisodeNumberExtractor}
     * — nunca duplicar essa extração por conta própria). Sem número extraído (ex.: filme sem
     * numeração, só nome de arco), cai pro próprio nome em minúsculas, pra ordenar
     * alfabeticamente.
     *
     * @param name nome do arquivo
     * @return a chave de ordenação: número de episódio zero-preenchido quando extraído, senão
     *         o nome em minúsculas
     */
    private String naturalSortKeyOf(String name) {
        final OptionalInt episodeNumber = EpisodeNumberExtractor.extract(name);

        if (episodeNumber.isEmpty()) {
            return name.toLowerCase();
        }

        return String.format("%0" + EPISODE_NUMBER_PADDING_WIDTH + "d", episodeNumber.getAsInt());
    }

}
