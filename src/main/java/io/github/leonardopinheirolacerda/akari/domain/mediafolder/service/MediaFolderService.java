package io.github.leonardopinheirolacerda.akari.domain.mediafolder.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeReviewStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.mapper.MediaFolderMapper;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Pastas do catalog: CRUD administrado pela API e os métodos de suporte usados pela ingestão
 * (busca ou criação por path com resolução do pai, busca de subtree).
 */
@ApplicationScoped
public class MediaFolderService {

    @Inject
    MediaFolderMapper mediaFolderMapper;

    @Inject
    StorageProviderService storageProviderService;

    @Inject
    AnimeService animeService;

    /**
     * Cria uma nova pasta, resolvendo o pai automaticamente a partir do {@code path}.
     *
     * @param mediaFolderRequest dados da pasta a criar
     * @return a pasta recém-criada
     * @throws ResourceNotFoundException se o {@code storageProviderId} informado não existir
     * @throws BusinessRuleException se já existir uma pasta com o mesmo provider e path
     */
    @Transactional
    public MediaFolderResponse createFolder(MediaFolderRequest mediaFolderRequest) {
        Log.infof(
                "Criando pasta no provider %d: %s",
                mediaFolderRequest.getStorageProviderId(),
                mediaFolderRequest.getPath()
        );

        final StorageProvider storageProvider = storageProviderService
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

        return mediaFolderMapper.toResponse(folder);

    }

    /**
     * Substitui os campos mutáveis de uma pasta existente, redescobrindo o pai a partir do
     * novo {@code path}.
     *
     * @param folderId id da pasta a atualizar
     * @param mediaFolderRequest novos dados da pasta
     * @return a pasta atualizada
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     * @throws BusinessRuleException se outra pasta já existir com o mesmo provider e novo path
     */
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

        final StorageProvider storageProvider = storageProviderService
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

        return mediaFolderMapper.toResponse(folder);
    }

    /**
     * Remove uma pasta — descendentes e arquivos abrigados descem pela cascata do DDL.
     *
     * @param folderId id da pasta a remover
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     */
    @Transactional
    public void deleteFolder(Integer folderId) {
        Log.infof("Removendo pasta %d", folderId);

        final MediaFolder folder = findOrThrow(folderId);

        folder.delete();

        Log.infof("Pasta %d removida com sucesso", folderId);
    }

    /**
     * Busca uma pasta por id.
     *
     * @param folderId id da pasta
     * @return a pasta
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     */
    public MediaFolderResponse getFolder(Integer folderId) {
        final MediaFolder folder = findOrThrow(folderId);

        return mediaFolderMapper.toResponse(folder);
    }

    /**
     * Pagina as pastas, aplicando os filtros informados como AND, já com o binding de anime de
     * cada uma embutido — evita o cliente paginar e depois buscar os bindings em lote numa
     * segunda chamada.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param name substring case-insensitive do nome; {@code null} não filtra
     * @param mediaType tipo de mídia; {@code null} não filtra
     * @param storageProviderId id do provider; {@code null} não filtra
     * @param parentId id da pasta pai; {@code null} não filtra
     * @param hasFiles filtra por ter ou não arquivos diretos; {@code null} não filtra
     * @param reviewStatus status de revisão do anime vinculado; {@code null} não filtra
     * @param hasAnilist filtra por ter ou não anime vinculado à AniList; {@code null} não filtra
     * @param hasTmdb filtra por ter ou não binding TMDB; {@code null} não filtra
     * @param allEpisodesPaired filtra por ter todo arquivo pareado ao TMDB; {@code null} não
     *                          filtra
     * @param sort specs {@code campo[:asc|desc]} na whitelist {@code name}/{@code createdAt}
     * @return página de pastas que atendem aos filtros, cada uma com {@code animeBinding}
     *         preenchido quando vinculada
     */
    public MediaFolderPage listFolders(
            Integer page,
            Integer size,
            String name,
            MediaType mediaType,
            Integer storageProviderId,
            Integer parentId,
            Boolean hasFiles,
            AnimeReviewStatus reviewStatus,
            Boolean hasAnilist,
            Boolean hasTmdb,
            Boolean allEpisodesPaired,
            List<String> sort) {

        final PageResult<MediaFolder> pageResult = MediaFolder.findPage(
                page,
                size,
                name,
                mediaType,
                storageProviderId,
                parentId,
                hasFiles,
                reviewStatus,
                hasAnilist,
                hasTmdb,
                allEpisodesPaired,
                sort
        );

        Log.infof("Listadas %d pastas (página %d, tamanho %d)", pageResult.data().size(), page, size);

        final MediaFolderPage folderPage = mediaFolderMapper.toPage(pageResult);

        final List<Integer> folderIds = folderPage.getData()
                .stream()
                .map(MediaFolderResponse::getId)
                .toList();

        final Map<Integer, AnimeBindingSummary> bindingsByFolderId = animeService.getAnimeBindings(folderIds)
                .stream()
                .collect(Collectors.toMap(AnimeBindingSummary::getFolderId, Function.identity()));

        folderPage.getData()
                .forEach(folder -> folder.animeBinding(bindingsByFolderId.get(folder.getId())));

        return folderPage;
    }

    /**
     * Busca uma pasta por id, ou lança se não existir.
     *
     * @param folderId id da pasta
     * @return a pasta
     * @throws ResourceNotFoundException se o {@code folderId} não existir
     */
    public MediaFolder findOrThrow(Integer folderId) {
        return MediaFolder.find(folderId)
                .orElseThrow(() -> {
                    Log.warnf("Pasta %d não encontrada", folderId);
                    return new ResourceNotFoundException("Não foi possível localizar uma pasta com o id informado");
                });
    }

    /**
     * Busca todas as pastas de um subtree do provider.
     *
     * @param storageProviderId id do provider
     * @param rootPath raiz do subtree; vazio significa o provider inteiro
     * @return as pastas do subtree
     */
    public List<MediaFolder> findSubtree(Integer storageProviderId, String rootPath) {
        return MediaFolder.findSubtree(storageProviderId, rootPath);
    }

    /**
     * Devolve a pasta do provider com esse {@code path}, criando-a se ainda não existir.
     *
     * <p>Na criação, o pai é resolvido pelo path ({@code parentOf(path)}); se o pai ainda não
     * estiver no catalog, a pasta fica sem pai. Por isso a ingestão cria as pastas da mais rasa
     * pra mais funda, cada uma na sua transação. Pasta já existente é devolvida como está, sem
     * atualizar {@code name}, {@code mediaType} nem o pai. Não há constraint de unicidade em
     * {@code (storage_provider_id, path)}: duas chamadas concorrentes pro mesmo path podem
     * criar duplicatas.
     *
     * @param storageProviderId id do provider
     * @param path path completo da pasta no provider
     * @param name nome da pasta, gravado só na criação
     * @param mediaType tipo de mídia, gravado só na criação
     * @return a pasta existente ou a recém-criada
     * @throws ResourceNotFoundException se a pasta não existir e o {@code storageProviderId}
     *         também não
     */
    @Transactional
    public MediaFolder findOrCreate(Integer storageProviderId, String path, String name, MediaType mediaType) {

        final Optional<MediaFolder> optFolder = MediaFolder.findByProviderAndPath(storageProviderId, path);

        if (optFolder.isPresent()) {
            return optFolder.get();
        }

        final StorageProvider storageProvider = storageProviderService.findOrThrow(storageProviderId);

        final String parentPath = DirectoryUtils.parentOf(path);
        final MediaFolder parent = MediaFolder.findByProviderAndPath(storageProviderId, parentPath)
                .orElse(null);

        final MediaFolder folder = new MediaFolder();

        folder.mediaType = mediaType;
        folder.name = name;
        folder.path = path;
        folder.parent = parent;
        folder.storageProvider = storageProvider;

        folder.persist();

        Log.infof("Pasta %d criada com sucesso", folder.id);

        return folder;
    }

}
