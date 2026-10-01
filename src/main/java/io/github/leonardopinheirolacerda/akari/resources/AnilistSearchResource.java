package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.AnilistSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;

public class AnilistSearchResource implements AnilistSearchApi {

    // Busca sem resultado: página vazia, não 404.
    @Override
    public AnilistMediaSummaryPage searchAnilistMedia(
            String q,
            Integer page,
            Integer size,
            Boolean forceRefresh) {
        throw new WebApplicationException(Response.Status.NOT_IMPLEMENTED);
    }
}
