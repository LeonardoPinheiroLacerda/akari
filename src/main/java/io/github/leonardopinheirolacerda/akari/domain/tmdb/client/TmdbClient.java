package io.github.leonardopinheirolacerda.akari.domain.tmdb.client;

import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbImagesResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieSearchResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTranslationsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvSearchResponse;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.rest.client.annotation.RegisterClientHeaders;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/**
 * REST Client (MicroProfile) para a API v3 do TMDB.
 *
 * <p>URL base resolvida por {@code quarkus.rest-client.tmdb.url}. Autenticação via header
 * {@code Authorization: Bearer <token>}, injetado automaticamente pelo
 * {@link TmdbAuthHeadersFactory} em toda chamada — o token vem da preferência
 * {@code tmdb.api-key}.
 *
 * <p>Esta interface é o transporte cru — não faz retry nem tradução de exceções. Fault
 * tolerance e embrulho em {@code IntegrationException} ficam no service que chamar.
 */
@RegisterRestClient(configKey = "tmdb")
@RegisterClientHeaders(TmdbAuthHeadersFactory.class)
@Produces(MediaType.APPLICATION_JSON)
public interface TmdbClient {

    /**
     * {@code GET /3/search/movie} — busca por filmes.
     *
     * @param query texto de busca
     * @param page número da página (1-based, convenção do TMDB)
     * @param language tag BCP 47 já resolvida
     * @param includeAdult se {@code true}, inclui conteúdo adulto
     * @return envelope paginado com os resultados
     */
    @GET
    @Path("/3/search/movie")
    TmdbMovieSearchResponse searchMovie(
            @QueryParam("query") String query,
            @QueryParam("page") Integer page,
            @QueryParam("language") String language,
            @QueryParam("include_adult") boolean includeAdult);

    /**
     * {@code GET /3/search/tv} — busca por séries.
     *
     * @param query texto de busca
     * @param page número da página (1-based, convenção do TMDB)
     * @param language tag BCP 47 já resolvida
     * @param includeAdult se {@code true}, inclui conteúdo adulto
     * @return envelope paginado com os resultados
     */
    @GET
    @Path("/3/search/tv")
    TmdbTvSearchResponse searchTv(
            @QueryParam("query") String query,
            @QueryParam("page") Integer page,
            @QueryParam("language") String language,
            @QueryParam("include_adult") boolean includeAdult);

    /** {@code GET /3/movie/{id}} — detalhe de filme. */
    @GET
    @Path("/3/movie/{movie_id}")
    TmdbMovieDetailsResponse getMovieDetails(@PathParam("movie_id") Integer movieId);

    /** {@code GET /3/movie/{id}/images} — imagens de filme. */
    @GET
    @Path("/3/movie/{movie_id}/images")
    TmdbImagesResponse getMovieImages(@PathParam("movie_id") Integer movieId);

    /** {@code GET /3/movie/{id}/translations} — traduções de filme ({@code data.title}). */
    @GET
    @Path("/3/movie/{movie_id}/translations")
    TmdbTranslationsResponse getMovieTranslations(@PathParam("movie_id") Integer movieId);

    /** {@code GET /3/tv/{id}} — detalhe de série. */
    @GET
    @Path("/3/tv/{series_id}")
    TmdbTvDetailsResponse getTvDetails(@PathParam("series_id") Integer seriesId);

    /** {@code GET /3/tv/{id}/images} — imagens de série. */
    @GET
    @Path("/3/tv/{series_id}/images")
    TmdbImagesResponse getTvImages(@PathParam("series_id") Integer seriesId);

    /** {@code GET /3/tv/{id}/translations} — traduções de série ({@code data.name}). */
    @GET
    @Path("/3/tv/{series_id}/translations")
    TmdbTranslationsResponse getTvTranslations(@PathParam("series_id") Integer seriesId);

    /** {@code GET /3/tv/{id}/season/{n}} — episódios de uma temporada. */
    @GET
    @Path("/3/tv/{series_id}/season/{season_number}")
    TmdbSeasonResponse getSeason(
            @PathParam("series_id") Integer seriesId,
            @PathParam("season_number") Integer seasonNumber);

}