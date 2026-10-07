package io.github.leonardopinheirolacerda.akari.domain.anilist.resource;

import io.github.leonardopinheirolacerda.akari.api.AnilistSearchApi;
import io.github.leonardopinheirolacerda.akari.api.dto.AnilistMediaSummaryPage;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class AnilistSearchResource implements AnilistSearchApi {

    @Inject
    AnilistSearchService anilistSearchService;

    // Busca sem resultado: página vazia, não 404.
    @Override
    public AnilistMediaSummaryPage searchAnilistMedia(
            String q,
            Integer page,
            Integer size,
            Boolean forceRefresh) {
        return anilistSearchService.search(q, page, size, forceRefresh);
    }
}
