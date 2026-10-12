package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeRelationsApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimePage;
import io.github.leonardopinheirolacerda.akari.api.dto.AnimeRelationGraphResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeRelationsService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeRelationsApi} — grafo de relações entre animes (sequel/prequel/side
 * story) e a listagem de animes-raiz, em {@code /v1/metadata/anime-relations} e
 * {@code /v1/metadata/animes/{anilistId}/relations/graph}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link AnimeRelationsService}.
 */
public class AnimeRelationsResource implements AnimeRelationsApi {

    @Inject
    AnimeRelationsService animeRelationsService;

    /**
     * {@code GET /v1/metadata/animes/{anilistId}/relations/graph} — materializa o grafo de
     * relações a partir do anime informado, via BFS bidirecional até {@code depth} saltos.
     *
     * @param anilistId anime que ancora a busca
     * @param depth profundidade máxima da BFS
     * @return o grafo materializado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se não há metadata salva pro id
     *         informado
     */
    @Override
    public AnimeRelationGraphResponse getAnimeRelationGraph(Integer anilistId, Integer depth) {
        return animeRelationsService.getAnimeRelationGraph(anilistId, depth);
    }

    /**
     * {@code GET /v1/metadata/anime-relations} — lista os animes-raiz da coleção, paginados.
     *
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @return a página de animes-raiz, com {@code 200 OK}
     */
    @Override
    public AnimePage listRelationRoots(Integer page, Integer size) {
        return animeRelationsService.listRelationRoots(page, size);
    }
}
