package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.AnimeApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeBindingSummary;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeFranchiseRootRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeView;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

import java.util.List;

public class AnimeResource implements AnimeApi {

    @Override
    public AnimeView bindAnimeToFolder(Integer anilistId, AnimeBindingRequest animeBindingRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void deleteAnime(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public AnimeView getAnime(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public List<AnimeBindingSummary> getAnimeBindings(List<Integer> folderIds) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public AnimeView getAnimeByFolder(Integer folderId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public AnimeView setAnimeFranchiseRoot(
            Integer anilistId,
            AnimeFranchiseRootRequest animeFranchiseRootRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public AnimeView syncAnime(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
