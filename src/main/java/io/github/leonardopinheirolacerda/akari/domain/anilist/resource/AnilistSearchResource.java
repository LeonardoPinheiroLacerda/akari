package io.github.leonardopinheirolacerda.akari.domain.anilist.resource;

import io.github.leonardopinheirolacerda.akari.api.AnilistSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnilistSearchApi} — busca paginada de anime na AniList, em
 * {@code /v1/anilist/search}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e o cache-first ficam
 * no {@link AnilistSearchService}.
 */
@ApplicationScoped
public class AnilistSearchResource implements AnilistSearchApi {

    @Inject
    AnilistSearchService anilistSearchService;

    /**
     * {@code GET /v1/anilist/search} — busca paginada de anime na AniList.
     *
     * @param q termo de busca
     * @param page página desejada, 0-based
     * @param size tamanho da página
     * @param forceRefresh se {@code true}, ignora o cache e busca de novo na AniList
     * @return a página de resumos encontrados, com {@code 200 OK}; busca sem resultado devolve
     *         página vazia, não {@code 404}
     * @throws IntegrationException (traduzida em 502) se a chamada à AniList falhar
     */
    @Override
    public AnilistMediaSummaryPage searchAnilistMedia(
            String q,
            Integer page,
            Integer size,
            Boolean forceRefresh) {
        return anilistSearchService.search(q, page, size, forceRefresh);
    }
}
