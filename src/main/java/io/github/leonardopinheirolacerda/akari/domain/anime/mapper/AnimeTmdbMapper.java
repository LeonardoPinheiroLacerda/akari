package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbGenreResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbMovieDetailsResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbTvDetailsResponse;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte o estado TMDB (binding + overrides) do model {@link Anime} e a ficha da série/filme
 * vinculado pros DTOs gerados do contrato.
 */
@Mapper(componentModel = "cdi", imports = TmdbImageUtils.class)
public interface AnimeTmdbMapper {

    /**
     * Estado TMDB do anime: binding e overrides do model, mais a ficha do título vinculado.
     *
     * @param anime anime com binding TMDB
     * @param details ficha resumida da série/filme vinculado, lida do TMDB
     * @return o estado TMDB
     */
    @Mapping(target = "mediaType", source = "anime.tmdbMediaType")
    @Mapping(target = "createdAt", source = "anime.tmdbBoundAt")
    @Mapping(target = "overrides", expression = "java(toOverrides(anime))")
    TmdbAnimeStateResponse toState(Anime anime, TmdbBindingDetails details);

    /**
     * Bloco de overrides, ou {@code null} quando nenhum dos 5 campos foi definido — o estado
     * "sem overrides" é representado pela ausência do bloco, não por um bloco com tudo nulo.
     *
     * @param anime model com os campos de override
     * @return o bloco de overrides, ou {@code null}
     */
    default TmdbOverrides toOverrides(Anime anime) {
        if (anime.overrideTitle == null
                && anime.overrideSynopsis == null
                && anime.overridePosterPath == null
                && anime.overrideBackdropPath == null
                && anime.overrideLogoPath == null) {
            return null;
        }

        return new TmdbOverrides()
                .title(anime.overrideTitle)
                .synopsis(anime.overrideSynopsis)
                .posterPath(anime.overridePosterPath)
                .posterPreviewUrl(TmdbImageUtils.posterMedium(anime.overridePosterPath))
                .backdropPath(anime.overrideBackdropPath)
                .backdropPreviewUrl(TmdbImageUtils.backdrop(anime.overrideBackdropPath))
                .logoPath(anime.overrideLogoPath)
                .logoPreviewUrl(TmdbImageUtils.logo(anime.overrideLogoPath));
    }

    /**
     * Ficha resumida de uma série do TMDB, sem overrides aplicados.
     *
     * @param tv ficha da série devolvida por {@code GET /3/tv/{id}}
     * @return os dados da série, com {@code runtime} {@code null} (só existe em filme)
     */
    @Mapping(target = "title", source = "name")
    @Mapping(target = "originalTitle", source = "originalName")
    @Mapping(target = "releaseDate", source = "firstAirDate")
    @Mapping(target = "runtime", ignore = true)
    @Mapping(target = "posterUrl", expression = "java(TmdbImageUtils.posterMedium(tv.posterPath()))")
    @Mapping(target = "backdropUrl", expression = "java(TmdbImageUtils.backdrop(tv.backdropPath()))")
    TmdbBindingDetails toDetails(TmdbTvDetailsResponse tv);

    /**
     * Ficha resumida de um filme do TMDB, sem overrides aplicados.
     *
     * @param movie ficha do filme devolvida por {@code GET /3/movie/{id}}
     * @return os dados do filme, com {@code numberOfSeasons}/{@code numberOfEpisodes}
     *         {@code null} (só existem em série)
     */
    @Mapping(target = "numberOfSeasons", ignore = true)
    @Mapping(target = "numberOfEpisodes", ignore = true)
    @Mapping(target = "posterUrl", expression = "java(TmdbImageUtils.posterMedium(movie.posterPath()))")
    @Mapping(target = "backdropUrl", expression = "java(TmdbImageUtils.backdrop(movie.backdropPath()))")
    TmdbBindingDetails toDetails(TmdbMovieDetailsResponse movie);

    /**
     * Nome do gênero; usado pelo MapStruct pra converter a lista de gêneros da ficha.
     *
     * @param genre gênero da ficha do TMDB
     * @return o nome do gênero, no idioma da ficha
     */
    default String toGenreName(TmdbGenreResponse genre) {
        return genre.name();
    }

}
