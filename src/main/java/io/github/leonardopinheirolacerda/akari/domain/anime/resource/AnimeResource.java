package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseRootRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeApi} — binding anime↔pasta, fetch/sync na AniList e curadoria manual
 * da raiz de franquia, em {@code /v1/metadata/animes}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link AnimeService}.
 */
public class AnimeResource implements AnimeApi {

    @Inject
    AnimeService animeService;

    /**
     * {@code POST /v1/metadata/animes/{anilistId}/binding} — busca o anime na AniList e cria a
     * metadata vinculada a uma pasta do catalog.
     *
     * @param anilistId id do anime na AniList
     * @param animeBindingRequest payload com o {@code folderId} a vincular
     * @return a metadata recém-criada, com {@code 201 Created}
     * @throws BusinessRuleException (traduzida em 400) se já existe metadata pro anime ou a
     *         pasta já está vinculada a outro anime
     */
    @Override
    public AnimeResponse bindAnimeToFolder(Integer anilistId, AnimeBindingRequest animeBindingRequest) {
        return animeService.bindAnimeToFolder(anilistId, animeBindingRequest);
    }

    /**
     * {@code DELETE /v1/metadata/animes/{anilistId}} — remove a metadata do anime, liberando a
     * pasta pra um novo binding.
     *
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public void deleteAnime(Integer anilistId) {
        animeService.deleteAnime(anilistId);
    }

    /**
     * {@code GET /v1/metadata/animes/{anilistId}} — lê a metadata local do anime, sem buscar de
     * novo na AniList.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata salva, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public AnimeResponse getAnime(Integer anilistId) {
        return animeService.getAnime(anilistId);
    }

    /**
     * {@code GET /v1/metadata/folders/{folderId}/anime} — lê a metadata do anime vinculado à
     * pasta informada.
     *
     * @param folderId id da pasta no catalog
     * @return a metadata do anime vinculado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se a pasta não tem anime vinculado
     */
    @Override
    public AnimeResponse getAnimeByFolder(Integer folderId) {
        return animeService.getAnimeByFolder(folderId);
    }

    /**
     * {@code PUT /v1/metadata/animes/{anilistId}/review} — confirma manualmente que a ingestão
     * desse anime está correta, travando o campo contra alterações futuras do auto-bind.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public AnimeResponse markAnimeAsReviewed(Integer anilistId) {
        return animeService.markAsReviewed(anilistId);
    }

    /**
     * {@code PUT /v1/metadata/animes/{anilistId}/franchise-root} — atualiza a curadoria manual
     * da flag de raiz de franquia.
     *
     * @param anilistId id do anime na AniList
     * @param animeFranchiseRootRequest payload com a nova curadoria
     * @return a metadata atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public AnimeResponse setAnimeFranchiseRoot(
            Integer anilistId,
            AnimeFranchiseRootRequest animeFranchiseRootRequest) {
        return animeService.setAnimeFranchiseRoot(anilistId, animeFranchiseRootRequest);
    }

    /**
     * {@code POST /v1/metadata/animes/{anilistId}/sync} — busca o anime de novo na AniList e
     * atualiza a metadata, mantendo a pasta vinculada e a curadoria manual da raiz de franquia.
     *
     * @param anilistId id do anime na AniList
     * @return a metadata atualizada, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public AnimeResponse syncAnime(Integer anilistId) {
        return animeService.syncAnime(anilistId);
    }
}
