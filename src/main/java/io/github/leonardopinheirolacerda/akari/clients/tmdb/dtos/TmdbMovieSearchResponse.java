package io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Envelope de {@code GET /3/search/movie} — TMDB é 1-based e pagina em blocos fixos de 20.
 *
 * @param page número da página atual (1-based)
 * @param results itens da página; pode ser vazio
 * @param totalPages total de páginas disponíveis para a query
 * @param totalResults total absoluto de resultados
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record TmdbMovieSearchResponse(
        Integer page,
        List<TmdbMovieSearchItemResponse> results,
        @JsonProperty("total_pages") Integer totalPages,
        @JsonProperty("total_results") Integer totalResults
) {
}
