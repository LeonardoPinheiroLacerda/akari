package io.github.leonardopinheirolacerda.akari.domain.ingestion.service;

import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Remove pastas do catalog junto com o anime vinculado a cada uma — usado tanto pelo {@code
 * DELETE} explícito do contrato ({@link IngestionService#removeIngestedSubtree}) quanto pelo
 * Step 2 da reconciliação ({@link ReconciliationService#reconcileRemovals}), que só decidem
 * quais pastas remover, nunca como. Fica à parte de propósito: remover pasta não é reconciliar
 * (comparar provider × catalog), é só deleção — as duas chamadoras não têm relação semântica
 * entre si além de usar a mesma mecânica.
 */
@ApplicationScoped
public class FolderRemovalService {

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    AnimeService animeService;

    /**
     * Busca o subtree e remove via {@link #removeFolders} — usado pelo {@code DELETE} explícito.
     * Sempre idempotente: nada bate, nada a fazer.
     *
     * @param providerId id do storage provider
     * @param rootPath subtree a remover; vazio remove o provider inteiro
     */
    @Transactional
    public void removeSubtree(Integer providerId, String rootPath) {
        final List<MediaFolder> folders = mediaFolderService.findSubtree(providerId, rootPath);

        removeFolders(folders);
    }

    /**
     * Remove os animes vinculados primeiro (FK {@code anime.folder_id} sem cascade — deletar a
     * pasta falharia com o anime ainda vinculado), só então as pastas — {@link
     * MediaFolderService#deleteRoots} cuida do resto (raízes do conjunto, flush, cascade).
     *
     * @param folders pastas a remover (o conjunto inteiro, não só as raízes)
     */
    @Transactional
    public void removeFolders(List<MediaFolder> folders) {
        for (MediaFolder folder : folders) {
            animeService.findByFolder(folder.id)
                    .map(anime -> anime.anilistId)
                    .ifPresent(animeService::deleteAnime);
        }

        mediaFolderService.deleteRoots(folders);
    }

}
