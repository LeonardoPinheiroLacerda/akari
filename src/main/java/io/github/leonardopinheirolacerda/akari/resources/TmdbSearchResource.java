package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.TmdbSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResultPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResultPage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class TmdbSearchResource implements TmdbSearchApi {

    // Busca sem resultado: página vazia, não 404.
    @Override
    public TmdbMovieSearchResultPage searchTmdbMovie(
            String q,
            Integer page,
            Integer size,
            String language,
            Boolean forceRefresh) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }

    // Busca sem resultado: página vazia, não 404.
    @Override
    public TmdbTvSearchResultPage searchTmdbTv(
            String q,
            Integer page,
            Integer size,
            String language,
            Boolean forceRefresh) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
