package io.github.leonardopinheirolacerda.akari.resources;

import io.github.leonardopinheirolacerda.akari.api.TmdbSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResultPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResultPage;
import io.github.leonardopinheirolacerda.akari.services.TmdbSearchService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class TmdbSearchResource implements TmdbSearchApi {

    @Inject
    TmdbSearchService tmdbSearchService;

    // Busca sem resultado: página vazia, não 404.
    @Override
    public TmdbMovieSearchResultPage searchTmdbMovie(
            String q,
            Integer page,
            String language,
            Boolean forceRefresh) {
        return tmdbSearchService.searchMovies(q, page, language, forceRefresh);
    }

    // Busca sem resultado: página vazia, não 404.
    @Override
    public TmdbTvSearchResultPage searchTmdbTv(
            String q,
            Integer page,
            String language,
            Boolean forceRefresh) {
        return tmdbSearchService.searchTv(q, page, language, forceRefresh);
    }
}
