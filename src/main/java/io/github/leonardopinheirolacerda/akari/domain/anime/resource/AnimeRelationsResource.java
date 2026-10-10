package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeRelationsApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeRelationsService;
import jakarta.inject.Inject;

/**
 * Grafo de relações entre animes (sequel/prequel/side story) e a listagem de animes-raiz.
 */
public class AnimeRelationsResource implements AnimeRelationsApi {

    @Inject
    AnimeRelationsService animeRelationsService;

    @Override
    public AnimeRelationGraphResponse getAnimeRelationGraph(Integer anilistId, Integer depth) {
        return animeRelationsService.getAnimeRelationGraph(anilistId, depth);
    }

    @Override
    public AnimePage listRelationRoots(Integer page, Integer size) {
        return animeRelationsService.listRelationRoots(page, size);
    }
}
