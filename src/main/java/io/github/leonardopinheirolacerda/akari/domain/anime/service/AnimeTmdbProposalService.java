package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Optional;

/**
 * Proposta de binding TMDB pra um anime da AniList, a partir do dataset do anibridge — só
 * consulta, nada é gravado.
 */
@ApplicationScoped
public class AnimeTmdbProposalService {

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    AnimeTmdbService animeTmdbService;

    /**
     * Propõe a série ou filme do TMDB pro anime, pelo melhor candidato do anibridge — o mesmo
     * critério do bind automático da ingestão — e devolve a ficha do título pra conferência.
     *
     * <p>O id do MyAnimeList (fallback do anibridge) e o total de episódios (desempate entre
     * candidatos) vêm da AniList, então funciona mesmo que o anime ainda não esteja salvo no
     * akari.
     *
     * @param request anime da AniList a propor
     * @return o {@code tmdbId}/{@code mediaType} propostos e a ficha do título no TMDB
     * @throws ResourceNotFoundException se o anime não existir na AniList, se o anibridge não
     *                                     tiver mapeamento pra ele, ou se o {@code tmdbId}
     *                                     proposto não existir mais no TMDB
     * @throws IntegrationException se a AniList ou o TMDB falharem
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a ficha
     *                                           não estiver em cache
     */
    public TmdbBindingProposalResponse proposeTmdbBinding(TmdbBindingProposalRequest request) {
        final Integer anilistId = request.getAnilistId();
        final AnilistMediaResponse anilistMedia = anilistSearchService.findById(anilistId, false);

        final Optional<TmdbMappingCandidate> candidate = anibridgeResolutionService.resolveBestCandidate(
                anilistId,
                anilistMedia.idMal(),
                anilistMedia.episodes()
        );

        if (candidate.isEmpty()) {
            Log.warnf("Nenhum mapeamento TMDB no anibridge pro anime %d", anilistId);
            throw new ResourceNotFoundException(
                    "Não foi encontrado um mapeamento TMDB pra esse anime no anibridge");
        }

        final Integer tmdbId = candidate.get().tmdbId();
        final TmdbMediaType mediaType = candidate.get().mediaType();
        final TmdbBindingDetails details = animeTmdbService.fetchDetails(tmdbId, mediaType);

        Log.infof("Proposto o TMDB %d (%s) pro anime %d", tmdbId, mediaType, anilistId);

        return new TmdbBindingProposalResponse()
                .tmdbId(tmdbId)
                .mediaType(mediaType)
                .details(details);
    }

}
