package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * Envelope compartilhado de {@code GET /3/movie/{id}/images} e {@code GET /3/tv/{id}/images} —
 * os dois endpoints devolvem o mesmo shape.
 *
 * @param backdrops backdrops (banners) disponíveis
 * @param posters posters disponíveis
 * @param logos logos disponíveis
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbImagesResponse(
        List<TmdbImageResponse> backdrops,
        List<TmdbImageResponse> posters,
        List<TmdbImageResponse> logos
) {
}
