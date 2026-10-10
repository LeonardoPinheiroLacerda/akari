package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte o estado TMDB (binding + overrides) do model {@link Anime} pros DTOs gerados do
 * contrato.
 */
@Mapper(componentModel = "cdi")
public interface AnimeTmdbMapper {

    @Mapping(target = "mediaType", source = "tmdbMediaType")
    @Mapping(target = "createdAt", source = "tmdbBoundAt")
    @Mapping(target = "overrides", expression = "java(toOverrides(anime))")
    TmdbAnimeStateResponse toState(Anime anime);

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
                .backdropPath(anime.overrideBackdropPath)
                .logoPath(anime.overrideLogoPath);
    }

}
