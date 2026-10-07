package io.github.leonardopinheirolacerda.akari.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMovieSearchResult;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbTvSearchResult;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbMovieSearchItemResponse;
import io.github.leonardopinheirolacerda.akari.clients.tmdb.dtos.TmdbTvSearchItemResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;

import java.util.List;

@Mapper(componentModel = "cdi")
public interface TmdbMapper {

    @Mapping(source = "id", target = "tmdbId")
    @Mapping(source = "posterPath", target = "posterUrl", qualifiedByName = "posterUrl")
    @Mapping(source = "backdropPath", target = "backdropUrl", qualifiedByName = "backdropUrl")
    TmdbMovieSearchResult toMovieResult(TmdbMovieSearchItemResponse item);

    List<TmdbMovieSearchResult> toMovieResultList(List<TmdbMovieSearchItemResponse> items);

    @Mapping(source = "id", target = "tmdbId")
    @Mapping(source = "posterPath", target = "posterUrl", qualifiedByName = "posterUrl")
    @Mapping(source = "backdropPath", target = "backdropUrl", qualifiedByName = "backdropUrl")
    TmdbTvSearchResult toTvResult(TmdbTvSearchItemResponse item);

    List<TmdbTvSearchResult> toTvResultList(List<TmdbTvSearchItemResponse> items);

    // poster/backdrop vêm como path relativo do TMDB; o contrato pede URL absoluta já montada.
    @Named("posterUrl")
    default String toPosterUrl(String posterPath) {
        return posterPath == null ? null : "https://image.tmdb.org/t/p/w185" + posterPath;
    }

    @Named("backdropUrl")
    default String toBackdropUrl(String backdropPath) {
        return backdropPath == null ? null : "https://image.tmdb.org/t/p/original" + backdropPath;
    }

}
