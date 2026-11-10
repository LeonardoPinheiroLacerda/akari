package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeReviewStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.FranchiseAssignment;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.graph.FranchiseResolver;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.model.MediaFolder;
import io.github.leonardopinheirolacerda.akari.domain.mediafolder.service.MediaFolderService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Binding anime↔pasta, fetch/sync na AniList e status de revisão do anime.
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

    @Inject
    AnimeOverrideService animeOverrideService;

    @Inject
    VideoFileService videoFileService;

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
        return toResponse(bindAnime(anilistId, animeBindingRequest));
    }

    /**
     * Mesmo bind de {@link #bindAnimeToFolder}, devolvendo o model em vez da resposta — sem
     * pagar a resolução de franquias que a resposta exige.
     *
     * @param anilistId id do anime na AniList
     * @param animeBindingRequest payload com o {@code folderId} a vincular
     * @return o anime recém-criado
     * @throws BusinessRuleException se já existe metadata pro anime ou a pasta já está vinculada
     *                                a outro anime
     */
    @Transactional
    public Anime bindAnime(Integer anilistId, AnimeBindingRequest animeBindingRequest) {
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

        final AnilistMediaResponse anilistMedia = anilistSearchService.findById(anilistId, false);

        final Anime anime = new Anime();
        animeMapper.applyAnilistData(anilistMedia, anime);
        anime.folder = folder;
        anime.franchiseAssignment = FranchiseAssignment.AUTO;

        anime.persist();

        animeRelationsService.syncRelations(anilistMedia);

        Log.infof("Anime %d vinculado à pasta %d com sucesso", anilistId, folder.id);

        return anime;
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
        return toResponse(anime);
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

        return toResponse(anime);
    }

    /**
     * Busca o model cru do anime vinculado à pasta, sem lançar se não houver — usado pela
     * ingestão pra checar o binding antes de buscar na AniList. Leitura marcada
     * {@code @Transactional} pra funcionar fora de contexto de request.
     *
     * @param folderId id da pasta no catalog
     * @return o anime vinculado, se houver
     */
    @Transactional
    public Optional<Anime> findByFolder(Integer folderId) {
        return Anime.findByFolderId(folderId);
    }

    /**
     * Busca o model cru do anime pelo {@code anilistId}, sem lançar se não houver — leitura
     * marcada {@code @Transactional} pra funcionar fora de contexto de request.
     *
     * @param anilistId id do anime na AniList
     * @return o anime, se houver metadata salva
     */
    @Transactional
    public Optional<Anime> find(Integer anilistId) {
        return Anime.find(anilistId);
    }

    /**
     * Resume o binding de várias pastas numa chamada só. Pastas sem anime vinculado simplesmente
     * não aparecem no resultado. Usado por {@link MediaFolderService#listFolders} pra embutir
     * {@code animeBinding} na listagem, sem o cliente precisar de uma segunda chamada.
     *
     * @param folderIds ids das pastas no catalog
     * @return os resumos das pastas que têm anime vinculado
     */
    public List<AnimeBindingSummary> getAnimeBindings(List<Integer> folderIds) {
        return Anime.findByFolderIds(folderIds)
                .stream()
                .map(animeOverrideService::toBindingSummary)
                .toList();
    }

    /**
     * Busca o anime de novo na AniList e atualiza a metadata, mantendo a pasta vinculada e a
     * atribuição de franquia. Também ressincroniza as arestas do grafo de relações.
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

        return toResponse(anime);
    }

    /**
     * Remove a metadata do anime; a pasta volta a ficar livre pra um novo binding. Os animes
     * {@code MANUAL_MEMBER} na franquia dele voltam pra {@code AUTO}.
     *
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public void deleteAnime(Integer anilistId) {
        Log.infof("Removendo metadata do anime %d", anilistId);

        final Anime anime = findOrThrow(anilistId);

        Anime.resetManualMembers(anime);
        anime.delete();

        Log.infof("Metadata do anime %d removida com sucesso", anilistId);
    }

    /**
     * Confirma manualmente que a ingestão desse anime está correta — a partir daqui, nenhum
     * recálculo automático deve alterar esse campo.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata atualizada
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public AnimeResponse markAsReviewed(Integer anilistId) {
        Log.infof("Confirmando revisão manual do anime %d", anilistId);

        final Anime anime = findOrThrow(anilistId);

        anime.reviewStatus = AnimeReviewStatus.REVIEWED;

        return toResponse(anime);
    }

    /**
     * Grava o status de revisão do anime ao fim da task de ingestão da pasta dele:
     * {@code PENDING_REVIEW} quando a task terminou com todos os steps e o anime está completo —
     * filme (os overrides já foram preenchidos) ou série com todo arquivo da pasta pareado;
     * {@code PARTIAL} nos demais casos. Um anime já {@code REVIEWED} não é alterado.
     *
     * @param anilistId id do anime na AniList
     * @param stepsCompleted {@code true} se a task terminou {@code COMPLETED}
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    @Transactional
    public void updateReviewStatus(Integer anilistId, boolean stepsCompleted) {
        final Anime anime = findOrThrow(anilistId);

        if (anime.reviewStatus == AnimeReviewStatus.REVIEWED) {
            return;
        }

        if (stepsCompleted && isComplete(anime)) {
            anime.reviewStatus = AnimeReviewStatus.PENDING_REVIEW;
            return;
        }

        anime.reviewStatus = AnimeReviewStatus.PARTIAL;
    }

    /**
     * Busca o anime pelo {@code anilistId} ou lança 404. Usado também por outros services do
     * domínio (ex.: {@link AnimeTmdbService}) que precisam do model cru, não do DTO.
     *
     * @param anilistId id do anime na AniList
     * @return o anime
     * @throws ResourceNotFoundException se não há metadata salva pro id informado
     */
    public Anime findOrThrow(Integer anilistId) {
        return Anime.find(anilistId)
                .orElseThrow(() -> {
                    Log.warnf("Anime %d não encontrado", anilistId);
                    return new ResourceNotFoundException(
                            "Não foi possível localizar a metadata de um anime com o id informado");
                });
    }

    private AnimeResponse toResponse(Anime anime) {
        return animeOverrideService.toResponse(anime, FranchiseResolver.resolve());
    }

    /**
     * Filme: completo com o TMDB vinculado. Série: completa com todo arquivo da pasta pareado.
     */
    private boolean isComplete(Anime anime) {
        if (anime.tmdbMediaType == TmdbMediaType.MOVIE) {
            return true;
        }

        final long files = videoFileService.countByFolder(anime.folder.id);
        final long paired = TmdbFilePairing.findByFolderId(anime.folder.id)
                .size();

        return files > 0 && paired == files;
    }
}
