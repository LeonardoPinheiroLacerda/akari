package io.github.leonardopinheirolacerda.akari.domain.anime.resource;

import io.github.leonardopinheirolacerda.akari.api.AnimeTmdbProposalApi;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalResponse;
import io.github.leonardopinheirolacerda.akari.domain.anime.service.AnimeTmdbProposalService;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.inject.Inject;

/**
 * Implementa {@link AnimeTmdbProposalApi} — propostas de binding TMDB e de pairing de episódios a
 * partir do anibridge, em {@code /v1/metadata/tmdb/binding-proposals} e
 * {@code /v1/metadata/tmdb/file-pairing-proposals}.
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

    /**
     * {@code POST /v1/metadata/tmdb/file-pairing-proposals} — propõe o pairing TMDB de cada
     * arquivo da pasta do anime, sem gravar nada.
     *
     * @param tmdbFilePairingProposalRequest anime cujos arquivos devem receber a proposta
     * @return a proposta por arquivo, com {@code 200 OK}
     * @throws ResourceNotFoundException (traduzida em 404) se o anime não existir ou não tiver
     *         binding TMDB
     * @throws BusinessRuleException (traduzida em 400) se o binding TMDB for de filme
     */
    @Override
    public TmdbFilePairingProposalResponse proposeTmdbFilePairing(TmdbFilePairingProposalRequest tmdbFilePairingProposalRequest) {
        return animeTmdbProposalService.proposeTmdbFilePairing(tmdbFilePairingProposalRequest);
    }

}
