package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbAnimeStateResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverrides;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbOverridesSchema;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeTmdbApi} — binding TMDB (série ou filme) e os overrides manuais de
 * título, sinopse e imagens, em {@code /v1/metadata/animes/{anilistId}/tmdb} e correlatos.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra de negócio e mapeamento ficam no
 * {@link AnimeTmdbService}.
 */
public class AnimeTmdbResource implements AnimeTmdbApi {

    @Inject
    AnimeTmdbService animeTmdbService;

    /**
     * {@code POST /v1/metadata/animes/{anilistId}/tmdb/binding} — vincula o anime a uma
     * série/filme do TMDB e pré-preenche os overrides de imagem (melhor esforço, nunca falha o
     * bind).
     *
     * @param anilistId id do anime na AniList
     * @param tmdbBindingRequest payload com {@code tmdbId} e {@code mediaType}
     * @return o estado TMDB recém-criado, com {@code 201 Created}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir, ou o
     *         {@code tmdbId} não existir no TMDB pro {@code mediaType} informado
     * @throws BusinessRuleException (traduzida em 400) se o anime já tiver binding TMDB
     */
    @Override
    public TmdbAnimeStateResponse bindTmdb(Integer anilistId, TmdbBindingRequest tmdbBindingRequest) {
        return animeTmdbService.bindTmdb(anilistId, tmdbBindingRequest);
    }

    /**
     * {@code DELETE /v1/metadata/animes/{anilistId}/tmdb/binding} — remove o binding TMDB do
     * anime; os overrides caem junto.
     *
     * @param anilistId id do anime na AniList
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     */
    @Override
    public void clearTmdbBinding(Integer anilistId) {
        animeTmdbService.clearTmdbBinding(anilistId);
    }

    /**
     * {@code GET /v1/metadata/animes/{anilistId}/tmdb/overrides/schema} — opções concretas por
     * campo que aceita override (traduções de título/sinopse e imagens de poster/backdrop/logo
     * disponíveis no TMDB pro binding do anime).
     *
     * @param anilistId id do anime na AniList
     * @param forceRefresh se {@code true}, ignora o cache local e re-busca do TMDB
     * @return o schema com as opções por campo, com {@code 200 OK} (listas podem vir vazias)
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     */
    @Override
    public TmdbOverridesSchema getTmdbOverridesSchema(Integer anilistId, Boolean forceRefresh) {
        return animeTmdbService.getTmdbOverridesSchema(anilistId, forceRefresh);
    }

    /**
     * {@code GET /v1/metadata/animes/{anilistId}/tmdb} — lê o estado TMDB atual do anime
     * (binding + overrides).
     *
     * @param anilistId id do anime na AniList
     * @return o estado TMDB, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     */
    @Override
    public TmdbAnimeStateResponse getTmdbState(Integer anilistId) {
        return animeTmdbService.getTmdbState(anilistId);
    }

    /**
     * {@code PUT /v1/metadata/animes/{anilistId}/tmdb/overrides} — substitui o bloco inteiro de
     * overrides.
     *
     * @param anilistId id do anime na AniList
     * @param tmdbOverrides novo estado de overrides; todos os campos {@code null} apaga a linha
     *                      de overrides
     * @return o estado TMDB atualizado, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     */
    @Override
    public TmdbAnimeStateResponse updateTmdbOverrides(Integer anilistId, TmdbOverrides tmdbOverrides) {
        return animeTmdbService.updateTmdbOverrides(anilistId, tmdbOverrides);
    }
}
