package io.github.leonardopinheirolacerda.akari.domain.anibridge.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.AnimeProvider;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Consulta aos {@link MappingSegment} ingeridos do anibridge — três buscas separadas, uma por
 * id de origem (AniList, MAL) e uma pelo lado TMDB. Devolve a lista crua, sem escolher "melhor
 * candidato" quando há mais de um nem aplicar a conversão episódio-a-episódio — isso é
 * responsabilidade de quem consumir (o ingestion decide o que precisa a partir da lista).
 */
@ApplicationScoped
public class AnibridgeMappingService {

    @Transactional
    public List<MappingSegment> findByAnilistId(Integer anilistId) {
        return MappingSegment.findByAnimeProviderAndId(AnimeProvider.ANILIST, anilistId);
    }

    @Transactional
    public List<MappingSegment> findByMalId(Integer malId) {
        return MappingSegment.findByAnimeProviderAndId(AnimeProvider.MAL, malId);
    }

    @Transactional
    public List<MappingSegment> findByTmdbId(Integer tmdbId, TmdbMediaType tmdbMediaType) {
        return MappingSegment.findByTmdbIdAndMediaType(tmdbId, tmdbMediaType);
    }

}
