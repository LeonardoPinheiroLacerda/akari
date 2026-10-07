package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeRelationsApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeViewPage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class AnimeRelationsResource implements AnimeRelationsApi {

    @Override
    public AnimeRelationGraphResponse getAnimeRelationGraph(Integer anilistId, Integer depth) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public AnimeViewPage listRelationRoots(Integer page, Integer size) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
