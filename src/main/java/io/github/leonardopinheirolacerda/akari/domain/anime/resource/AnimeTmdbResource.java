package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverridesSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import jakarta.inject.Inject;

/**
 * Binding TMDB (série ou filme) e os overrides manuais de título, sinopse e imagens.
 */
public class AnimeTmdbResource implements AnimeTmdbApi {

    @Inject
    AnimeTmdbService animeTmdbService;

    @Override
    public TmdbAnimeStateResponse bindTmdb(Integer anilistId, TmdbBindingRequest tmdbBindingRequest) {
        return animeTmdbService.bindTmdb(anilistId, tmdbBindingRequest);
    }

    @Override
    public void clearTmdbBinding(Integer anilistId) {
        animeTmdbService.clearTmdbBinding(anilistId);
    }

    @Override
    public TmdbOverridesSchema getTmdbOverridesSchema(Integer anilistId, Boolean forceRefresh) {
        return animeTmdbService.getTmdbOverridesSchema(anilistId, forceRefresh);
    }

    @Override
    public TmdbAnimeStateResponse getTmdbState(Integer anilistId) {
        return animeTmdbService.getTmdbState(anilistId);
    }

    @Override
    public TmdbAnimeStateResponse updateTmdbOverrides(Integer anilistId, TmdbOverrides tmdbOverrides) {
        return animeTmdbService.updateTmdbOverrides(anilistId, tmdbOverrides);
    }
}
