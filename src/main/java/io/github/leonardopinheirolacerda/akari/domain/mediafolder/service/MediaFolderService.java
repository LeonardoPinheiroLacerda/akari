package io.github.leonardopinheirolacerda.akari.domain.mediafolder.service;

import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.model.PageResult;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.mapper.MediaFolderMapper;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.model.StorageProvider;
import io.github.leonardopinheirolacerda.akari.domain.storageprovider.service.StorageProviderService;
import io.github.leonardopinheirolacerda.akari.utils.DirectoryUtils;
import io.quarkus.hibernate.orm.panache.Panache;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Pastas do catalog: CRUD administrado pela API e os métodos de suporte usados pela ingestão
 * (resolução de pai por path, criação em lote, remoção em lote por raízes do subtree).
 */
@ApplicationScoped
public class MediaFolderService {

    @Inject
    MediaFolderMapper mediaFolderMapper;

    @Inject
    StorageProviderService storageProviderService;

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
     * Pagina as pastas, aplicando os filtros informados como AND.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param name substring case-insensitive do nome; {@code null} não filtra
     * @param mediaType tipo de mídia; {@code null} não filtra
     * @param storageProviderId id do provider; {@code null} não filtra
     * @param parentId id da pasta pai; {@code null} não filtra
     * @param hasFiles filtra por ter ou não arquivos diretos; {@code null} não filtra
     * @param sort specs {@code campo[:asc|desc]} na whitelist {@code name}/{@code createdAt}
     * @return página de pastas que atendem aos filtros
     */
    public MediaFolderPage listFolders(
            Integer page,
            Integer size,
            String name,
            MediaType mediaType,
            Integer storageProviderId,
            Integer parentId,
            Boolean hasFiles,
            List<String> sort) {

        final PageResult<MediaFolder> pageResult = MediaFolder.findPage(
                page,
                size,
                name,
                mediaType,
                storageProviderId,
                parentId,
                hasFiles,
                sort
        );

        Log.infof("Listadas %d pastas (página %d, tamanho %d)", pageResult.data().size(), page, size);

        return mediaFolderMapper.toPage(pageResult);
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
     * Busca a pasta pelo provider+path.
     *
     * @param storageProviderId id do provider
     * @param path path completo da pasta
     * @return a pasta, se existir
     */
    public Optional<MediaFolder> findByProviderAndPath(Integer storageProviderId, String path) {
        return MediaFolder.findByProviderAndPath(storageProviderId, path);
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
     * Cria a pasta, resolvendo o pai automaticamente pelo path — diferente de
     * {@link #createFolder}, não valida se já existe uma pasta com esse caminho (quem chama já
     * sabe que não existe); pensado pra reconciliação em lote da ingestão, onde cada pasta é
     * persistida em sua própria transação antes da próxima (filha) ser processada.
     *
     * @param storageProviderId id do provider
     * @param path path completo da pasta
     * @param name nome da pasta
     * @param mediaType tipo de mídia
     * @return a pasta recém-criada
     */
    @Transactional
    public MediaFolder create(Integer storageProviderId, String path, String name, MediaType mediaType) {
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

    /**
     * Remove só as raízes do conjunto informado (pai não está no próprio conjunto) — {@code ON
     * DELETE CASCADE} em {@code parent_id}/{@code folder_id}/{@code file_id} cuida do resto.
     *
     * <p>Usa {@code delete} em lote (por query), não {@code folder.delete()} de instância —
     * removendo uma pasta "raiz" enquanto outras pastas do mesmo lote (os filhos, ainda
     * carregados na mesma sessão porque vieram do mesmo {@code findSubtree}) têm uma associação
     * {@code @ManyToOne} pra ela ainda não resolvida faz o Hibernate lançar {@code
     * TransientPropertyValueException} ao tentar validar o grafo de entidades no flush — um
     * falso positivo confirmado empiricamente nesse domínio. O delete em lote manda o {@code
     * DELETE} direto pro banco, sem passar pela validação de grafo.
     *
     * <p>O {@link Panache#flush()} explícito antes garante que qualquer remoção pendente de
     * outra entidade referenciada (ex.: {@code Anime} vinculado à pasta, removido por quem
     * chama antes desse método) já esteja de fato no banco — senão o delete em lote da pasta
     * rodaria antes, e uma FK que ainda aponta pra ela bloquearia a operação.
     *
     * @param folders pastas do subtree a remover (o conjunto inteiro, não só as raízes)
     */
    @Transactional
    public void deleteRoots(List<MediaFolder> folders) {
        Panache.flush();

        final Set<Integer> folderIds = folders.stream()
                .map(folder -> folder.id)
                .collect(Collectors.toSet());

        for (MediaFolder folder : folders) {
            final boolean isRoot = folder.parent == null || !folderIds.contains(folder.parent.id);

            if (isRoot) {
                MediaFolder.delete("id = ?1", folder.id);
            }
        }
    }

}
