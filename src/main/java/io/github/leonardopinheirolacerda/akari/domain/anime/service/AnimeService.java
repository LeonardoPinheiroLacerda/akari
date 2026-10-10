package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseRootRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseRootCuration;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Binding anime↔pasta, fetch/sync na AniList e curadoria manual da raiz de franquia.
 */
@ApplicationScoped
public class AnimeService {

    @Inject
    AnimeMapper animeMapper;

    @Inject
    MediaFolderService mediaFolderService;

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    AnimeRelationsService animeRelationsService;

    /**
     * Busca o anime na AniList e cria a metadata vinculada à pasta informada. Também
     * sincroniza as arestas do grafo de relações com os animes já conhecidos localmente.
     *
     * @param anilistId id do anime na AniList
     * @param animeBindingRequest payload com o {@code folderId} a vincular
     * @return a metadata recém-criada
     * @throws BusinessRuleException se já existe metadata pro anime ou a pasta já está vinculada
     *                                a outro anime
     */
    @Transactional
    public AnimeResponse bindAnimeToFolder(Integer anilistId, AnimeBindingRequest animeBindingRequest) {
        Log.infof("Vinculando anime %d à pasta %d", anilistId, animeBindingRequest.getFolderId());

        if (Anime.find(anilistId).isPresent()) {
            Log.warnf("Anime %d já tem metadata salva", anilistId);
            throw new BusinessRuleException("Já existe metadata salva para esse anime");
        }

        final MediaFolder folder = mediaFolderService.findOrThrow(animeBindingRequest.getFolderId());

        if (Anime.findByFolderId(folder.id).isPresent()) {
            Log.warnf("Pasta %d já está vinculada a outro anime", folder.id);
            throw new BusinessRuleException("Essa pasta já está vinculada a um anime");
        }

        final AnilistMediaResponse anilistMedia = anilistSearchService.findById(anilistId, true);

        final Anime anime = new Anime();
        animeMapper.applyAnilistData(anilistMedia, anime);
        anime.folder = folder;
        anime.franchiseRootCuration = FranchiseRootCuration.AUTO;

        anime.persist();

        animeRelationsService.syncRelations(anilistMedia);

        Log.infof("Anime %d vinculado à pasta %d com sucesso", anilistId, folder.id);

        return animeMapper.toView(anime);
    }

    /**
     * Lê a metadata local do anime, sem buscar de novo na AniList.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata salva
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    public AnimeResponse getAnime(Integer anilistId) {
        final Anime anime = findOrThrow(anilistId);
        return animeMapper.toView(anime);
    }

    /**
     * Lê a metadata do anime vinculado à pasta informada.
     *
     * @param folderId id da pasta no catalog
     * @return a metadata do anime vinculado
     * @throws ResourceNotFoundException se a pasta não tem anime vinculado
     */
    public AnimeResponse getAnimeByFolder(Integer folderId) {
        final Anime anime = Anime.findByFolderId(folderId)
                .orElseThrow(() -> {
                    Log.warnf("Nenhum anime vinculado à pasta %d", folderId);
                    return new ResourceNotFoundException(
                            "Não foi possível localizar um anime vinculado à pasta informada");
                });

        return animeMapper.toView(anime);
    }

    /**
     * Resume o binding de várias pastas numa chamada só. Pastas sem anime vinculado simplesmente
     * não aparecem no resultado.
     *
     * @param folderIds ids das pastas no catalog
     * @return os resumos das pastas que têm anime vinculado
     */
    public List<AnimeBindingSummary> getAnimeBindings(List<Integer> folderIds) {
        final List<Anime> animes = Anime.findByFolderIds(folderIds);

        return animeMapper.toBindingSummaryList(animes);
    }

    /**
     * Busca o anime de novo na AniList e atualiza a metadata, mantendo a pasta vinculada e a
     * curadoria manual da raiz de franquia. Também ressincroniza as arestas do grafo de
     * relações.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata atualizada
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public AnimeResponse syncAnime(Integer anilistId) {
        Log.infof("Sincronizando anime %d com a AniList", anilistId);

        final Anime anime = findOrThrow(anilistId);

        final AnilistMediaResponse anilistMedia = anilistSearchService.findById(anilistId, true);

        animeMapper.applyAnilistData(anilistMedia, anime);

        animeRelationsService.syncRelations(anilistMedia);

        Log.infof("Anime %d sincronizado com sucesso", anilistId);

        return animeMapper.toView(anime);
    }

    /**
     * Remove a metadata do anime; a pasta volta a ficar livre pra um novo binding.
     *
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public void deleteAnime(Integer anilistId) {
        Log.infof("Removendo metadata do anime %d", anilistId);

        final Anime anime = findOrThrow(anilistId);

        anime.delete();

        Log.infof("Metadata do anime %d removida com sucesso", anilistId);
    }

    /**
     * Atualiza a curadoria manual da flag de raiz de franquia.
     *
     * @param anilistId id do anime na AniList
     * @param animeFranchiseRootRequest payload com a nova curadoria
     * @return a metadata atualizada
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public AnimeResponse setAnimeFranchiseRoot(Integer anilistId, AnimeFranchiseRootRequest animeFranchiseRootRequest) {
        Log.infof(
                "Atualizando curadoria de raiz de franquia do anime %d pra %s",
                anilistId,
                animeFranchiseRootRequest.getFranchiseRootCuration());

        final Anime anime = findOrThrow(anilistId);

        anime.franchiseRootCuration = animeFranchiseRootRequest.getFranchiseRootCuration();

        return animeMapper.toView(anime);
    }

    private Anime findOrThrow(Integer anilistId) {
        return Anime.find(anilistId)
                .orElseThrow(() -> {
                    Log.warnf("Anime %d não encontrado", anilistId);
                    return new ResourceNotFoundException(
                            "Não foi possível localizar a metadata de um anime com o id informado");
                });
    }

}
