package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseRootRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeService;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Binding anime↔pasta, fetch/sync na AniList e curadoria manual da raiz de franquia.
 */
public class AnimeResource implements AnimeApi {

    @Inject
    AnimeService animeService;

    @Override
    public AnimeResponse bindAnimeToFolder(Integer anilistId, AnimeBindingRequest animeBindingRequest) {
        return animeService.bindAnimeToFolder(anilistId, animeBindingRequest);
    }

    @Override
    public void deleteAnime(Integer anilistId) {
        animeService.deleteAnime(anilistId);
    }

    @Override
    public AnimeResponse getAnime(Integer anilistId) {
        return animeService.getAnime(anilistId);
    }

    @Override
    public List<AnimeBindingSummary> getAnimeBindings(List<Integer> folderIds) {
        return animeService.getAnimeBindings(folderIds);
    }

    @Override
    public AnimeResponse getAnimeByFolder(Integer folderId) {
        return animeService.getAnimeByFolder(folderId);
    }

    @Override
    public AnimeResponse setAnimeFranchiseRoot(
            Integer anilistId,
            AnimeFranchiseRootRequest animeFranchiseRootRequest) {
        return animeService.setAnimeFranchiseRoot(anilistId, animeFranchiseRootRequest);
    }

    @Override
    public AnimeResponse syncAnime(Integer anilistId) {
        return animeService.syncAnime(anilistId);
    }
}
