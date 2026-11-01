package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbProposalApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbProposalService;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeTmdbProposalApi} — proposta de binding TMDB a partir do anibridge, em
 * {@code /v1/metadata/tmdb/binding-proposals}.
 *
 * <p>Só traduz HTTP ↔ service, sem lógica própria: toda regra fica no
 * {@link AnimeTmdbProposalService}.
 */
public class AnimeTmdbProposalResource implements AnimeTmdbProposalApi {

    @Inject
    AnimeTmdbProposalService animeTmdbProposalService;

    /**
     * {@code POST /v1/metadata/tmdb/binding-proposals} — propõe a série/filme do TMDB pro anime
     * da AniList informado, sem gravar nada.
     *
     * @param tmdbBindingProposalRequest anime da AniList a propor
     * @return a proposta, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir na AniList, se
     *         o anibridge não tiver mapeamento pra ele, ou se o {@code tmdbId} proposto não
     *         existir mais no TMDB
     */
    @Override
    public TmdbBindingProposalResponse proposeTmdbBinding(TmdbBindingProposalRequest tmdbBindingProposalRequest) {
        return animeTmdbProposalService.proposeTmdbBinding(tmdbBindingProposalRequest);
    }

}
