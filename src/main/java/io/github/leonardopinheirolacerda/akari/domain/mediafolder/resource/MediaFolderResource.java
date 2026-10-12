package io.github.leonardopinheirolacerda.akari.domain.mediafolder.resource;

import io.github.leonardopinheirolacerda.akari.api.MediaFolderApi;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderPage;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaFolderResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.MediaType;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Implementa {@link MediaFolderApi} — pastas do catalog em {@code /v1/catalog/folders}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no
 * {@link MediaFolderService}.
 */
@ApplicationScoped
public class MediaFolderResource implements MediaFolderApi {

    @Inject
    MediaFolderService mediaFolderService;

    /**
     * {@code POST /v1/catalog/folders} — cria uma nova pasta.
     *
     * <p>A pasta pai é derivada do {@code path}; se não estiver registrada, a nova pasta vira
     * raiz ({@code parentId} nulo) — não é erro.
     *
     * @param mediaFolderRequest dados da pasta a criar
     * @return a pasta recém-criada, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code storageProviderId}
     *         informado não existir
     * @throws BusinessRuleException (traduzida em 400) se já existir uma pasta com o mesmo
     *         provider e path
     */
    @Override
    public MediaFolderResponse createFolder(MediaFolderRequest mediaFolderRequest) {
        return mediaFolderService.createFolder(mediaFolderRequest);
    }

    /**
     * {@code DELETE /v1/catalog/folders/{folderId}} — remove a pasta.
     *
     * <p>Descendentes e arquivos abrigados descem pela cascata do DDL.
     *
     * @param folderId id da pasta a remover
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code folderId} não existir
     */
    @Override
    public void deleteFolder(Integer folderId) {
        mediaFolderService.deleteFolder(folderId);
    }

    /**
     * {@code GET /v1/catalog/folders/{folderId}} — busca uma pasta por id.
     *
     * @param folderId id da pasta
     * @return a pasta, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code folderId} não existir
     */
    @Override
    public MediaFolderResponse getFolder(Integer folderId) {
        return mediaFolderService.getFolder(folderId);
    }

    /**
     * {@code GET /v1/catalog/folders} — lista paginada de pastas com filtros AND.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param name substring case-insensitive do nome; {@code null} não filtra
     * @param mediaType tipo de mídia; {@code null} não filtra
     * @param storageProviderId id do provider; {@code null} não filtra
     * @param parentId id da pasta pai; {@code null} não filtra
     * @param hasFiles filtra por ter ou não arquivos diretos; {@code null} não filtra
     * @param sort specs {@code campo[:asc|desc]} na whitelist {@code name}/{@code createdAt}
     * @return página de pastas, com {@code 200 OK}
     */
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
        return mediaFolderService.listFolders(
                page,
                size,
                name,
                mediaType,
                storageProviderId,
                parentId,
                hasFiles,
                sort
        );
    }

    /**
     * {@code PUT /v1/catalog/folders/{folderId}} — substitui os campos mutáveis da pasta.
     *
     * <p>Payload substituto (não patch). A pasta pai é redescoberta a partir do novo
     * {@code path}; {@code createdAt} é preservado.
     *
     * @param folderId id da pasta a atualizar
     * @param mediaFolderRequest novos dados da pasta
     * @return a pasta atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o {@code folderId} não existir
     * @throws BusinessRuleException (traduzida em 400) se outra pasta já existir com o mesmo
     *         provider e novo path
     */
    @Override
    public MediaFolderResponse updateFolder(Integer folderId, MediaFolderRequest mediaFolderRequest) {
        return mediaFolderService.updateFolder(folderId, mediaFolderRequest);
    }
}
