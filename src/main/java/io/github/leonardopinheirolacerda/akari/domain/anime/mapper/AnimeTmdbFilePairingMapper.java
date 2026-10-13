package io.github.leonardopinheirolacerda.akari.domain.anime.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchema;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchemaEpisode;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingSchemaSeason;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonResponse;
import io.github.leonardopinheirolacerda.akari.utils.TmdbImageUtils;
import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Converte entre o model {@link TmdbFilePairing}, as temporadas/episódios do TMDB, e os DTOs
 * gerados do contrato.
 */
@Mapper(componentModel = "cdi", imports = TmdbImageUtils.class)
public interface AnimeTmdbFilePairingMapper {

    @Mapping(target = "thumbnailPreviewUrl", expression = "java(TmdbImageUtils.thumbnail(pairing.thumbnailPath))")
    TmdbFilePairingResponse toResponse(TmdbFilePairing pairing);

    default TmdbFilePairingSchema toSchema(List<TmdbSeasonResponse> seasons) {
        final List<TmdbFilePairingSchemaSeason> seasonList = seasons
                .stream()
                .map(this::toSeason)
                .toList();

        return new TmdbFilePairingSchema()
                .seasons(seasonList);
    }

    default TmdbFilePairingSchemaSeason toSeason(TmdbSeasonResponse season) {
        final List<TmdbFilePairingSchemaEpisode> episodes = season.episodes() == null
                ? null
                : season.episodes()
                    .stream()
                    .map(this::toEpisode)
                    .toList();

        return new TmdbFilePairingSchemaSeason()
                .seasonNumber(season.seasonNumber())
                .name(season.name())
                .episodes(episodes);
    }

    default TmdbFilePairingSchemaEpisode toEpisode(TmdbEpisodeResponse episode) {
        return new TmdbFilePairingSchemaEpisode()
                .episodeNumber(episode.episodeNumber())
                .name(episode.name())
                .overview(episode.overview())
                .airDate(episode.airDate())
                .runtime(episode.runtime())
                .voteAverage(episode.voteAverage())
                .voteCount(episode.voteCount())
                .episodeType(episode.episodeType())
                .defaultStillPath(episode.stillPath())
                .defaultStillPreviewUrl(TmdbImageUtils.thumbnail(episode.stillPath()));
    }

}
