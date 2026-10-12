package io.github.leonardopinheirolacerda.akari.domain.tmdb.resource;

import io.github.leonardopinheirolacerda.akari.api.TmdbSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResultPage;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResultPage;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbSearchService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link TmdbSearchApi} — busca paginada de filme/série no TMDB, em
 * {@code /v1/tmdb/search/movies} e {@code /v1/tmdb/search/tv-shows}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e o cache-first ficam
 * no {@link TmdbSearchService}.
 */
@ApplicationScoped
public class TmdbSearchResource implements TmdbSearchApi {

    @Inject
    TmdbSearchService tmdbSearchService;

    /**
     * {@code GET /v1/tmdb/search/movies} — busca paginada de filme no TMDB.
     *
     * @param q termo de busca
     * @param page página desejada, 0-based
     * @param language idioma das traduções; {@code null} usa o default configurado
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return a página de resultados encontrados, com {@code 200 OK}; busca sem resultado
     *         devolve página vazia, não {@code 404}
     * @throws IntegrationException (traduzida em 502) se a chamada ao TMDB falhar
     */
    @Override
    public TmdbMovieSearchResultPage searchTmdbMovie(
            String q,
            Integer page,
            String language,
            Boolean forceRefresh) {
        return tmdbSearchService.searchMovies(q, page, language, forceRefresh);
    }

    /**
     * {@code GET /v1/tmdb/search/tv-shows} — busca paginada de série no TMDB.
     *
     * @param q termo de busca
     * @param page página desejada, 0-based
     * @param language idioma das traduções; {@code null} usa o default configurado
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo no TMDB
     * @return a página de resultados encontrados, com {@code 200 OK}; busca sem resultado
     *         devolve página vazia, não {@code 404}
     * @throws IntegrationException (traduzida em 502) se a chamada ao TMDB falhar
     */
    @Override
    public TmdbTvSearchResultPage searchTmdbTv(
            String q,
            Integer page,
            String language,
            Boolean forceRefresh) {
        return tmdbSearchService.searchTv(q, page, language, forceRefresh);
    }
}
