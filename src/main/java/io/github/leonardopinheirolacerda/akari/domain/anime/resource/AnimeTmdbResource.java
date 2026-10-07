package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverridesSchema;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class AnimeTmdbResource implements AnimeTmdbApi {

    @Override
    public TmdbAnimeStateResponse bindTmdb(
            Integer anilistId,
            TmdbBindingRequest tmdbBindingRequest) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public void clearTmdbBinding(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public TmdbOverridesSchema getTmdbOverridesSchema(Integer anilistId, Boolean forceRefresh) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public TmdbAnimeStateResponse getTmdbState(Integer anilistId) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    @Override
    public TmdbAnimeStateResponse updateTmdbOverrides(
            Integer anilistId,
            TmdbOverrides tmdbOverrides) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
