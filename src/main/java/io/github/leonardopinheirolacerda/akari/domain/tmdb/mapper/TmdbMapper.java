package io.github.leonardopinheirolacerda.akari.domain.tmdb.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResult;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResult;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieSearchItemResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvSearchItemResponse;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

/**
 * Converte os itens crus de busca do TMDB ({@code TmdbMovieSearchItemResponse}/
 * {@code TmdbTvSearchItemResponse}) pros DTOs de resposta do contrato.
 */
@Mapper(componentModel = "cdi")
public interface TmdbMapper {

    /**
     * Converte um item de busca de filme.
     *
     * @param item item cru retornado pelo TMDB
     * @return o resultado convertido, com {@code posterUrl}/{@code backdropUrl} já absolutas
     *         (ver {@link #toPosterUrl}/{@link #toBackdropUrl})
     */
    @Mapping(source = "id", target = "tmdbId")
    @Mapping(source = "posterPath", target = "posterUrl", qualifiedByName = "posterUrl")
    @Mapping(source = "backdropPath", target = "backdropUrl", qualifiedByName = "backdropUrl")
    TmdbMovieSearchResult toMovieResult(TmdbMovieSearchItemResponse item);

    /**
     * Converte uma lista de itens de busca de filme.
     *
     * @param items itens crus retornados pelo TMDB
     * @return os resultados convertidos, na mesma ordem
     */
    List<TmdbMovieSearchResult> toMovieResultList(List<TmdbMovieSearchItemResponse> items);

    /**
     * Converte um item de busca de série.
     *
     * @param item item cru retornado pelo TMDB
     * @return o resultado convertido, com {@code posterUrl}/{@code backdropUrl} já absolutas
     *         (ver {@link #toPosterUrl}/{@link #toBackdropUrl})
     */
    @Mapping(source = "id", target = "tmdbId")
    @Mapping(source = "posterPath", target = "posterUrl", qualifiedByName = "posterUrl")
    @Mapping(source = "backdropPath", target = "backdropUrl", qualifiedByName = "backdropUrl")
    TmdbTvSearchResult toTvResult(TmdbTvSearchItemResponse item);

    /**
     * Converte uma lista de itens de busca de série.
     *
     * @param items itens crus retornados pelo TMDB
     * @return os resultados convertidos, na mesma ordem
     */
    List<TmdbTvSearchResult> toTvResultList(List<TmdbTvSearchItemResponse> items);

    /**
     * Monta a URL absoluta do poster a partir do path relativo que o TMDB devolve.
     *
     * @param posterPath path relativo do poster; {@code null}/em branco devolve {@code null}
     * @return a URL absoluta (tamanho pequeno, {@code w185})
     */
    @Named("posterUrl")
    default String toPosterUrl(String posterPath) {
        return TmdbImageUtils.posterSmall(posterPath);
    }

    /**
     * Monta a URL absoluta do backdrop a partir do path relativo que o TMDB devolve.
     *
     * @param backdropPath path relativo do backdrop; {@code null}/em branco devolve {@code null}
     * @return a URL absoluta (tamanho {@code original})
     */
    @Named("backdropUrl")
    default String toBackdropUrl(String backdropPath) {
        return TmdbImageUtils.backdrop(backdropPath);
    }

}
