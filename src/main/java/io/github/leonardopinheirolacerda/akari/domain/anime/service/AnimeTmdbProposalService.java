package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposal;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anilist.client.dtos.AnilistMediaResponse;
import io.github.leonardopinheirolacerda.akari.domain.anilist.service.AnilistSearchService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.exceptions.BusinessRuleException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Optional;

/**
 * Propostas de binding TMDB e de pairing de episódios pra um anime, a partir do dataset do
 * anibridge — só consulta, nada é gravado.
 */
@ApplicationScoped
public class AnimeTmdbProposalService {

    @Inject
    AnilistSearchService anilistSearchService;

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    AnimeTmdbService animeTmdbService;

    @Inject
    EpisodePairingResolutionService episodePairingResolutionService;

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

    /**
     * Propõe o pairing TMDB de cada arquivo de vídeo da pasta do anime, exatamente como o
     * pairing automático da ingestão faria (ver {@link EpisodePairingResolutionService}) — sem
     * gravar nada.
     *
     * @param request anime cujos arquivos devem receber a proposta
     * @return a série vinculada e a proposta de cada arquivo da pasta, pareado ou com o motivo
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB
     * @throws BusinessRuleException se o binding TMDB for de filme
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e as
     *                                           temporadas não estiverem em cache
     */
    public TmdbFilePairingProposalResponse proposeTmdbFilePairing(TmdbFilePairingProposalRequest request) {
        final Anime anime = animeTmdbService.findBoundAnimeOrThrow(request.getAnilistId());

        if (anime.tmdbMediaType != TmdbMediaType.TV) {
            Log.warnf("Anime %d tem binding TMDB de filme, sem pairing de episódios", anime.anilistId);
            throw new BusinessRuleException("Pairing de episódios só existe pra binding TMDB de série");
        }

        final List<FilePairingResolution> resolutions = episodePairingResolutionService.resolve(
                anime,
                anime.folder.id,
                anime.folder.name
        );

        final List<TmdbFilePairingProposal> files = resolutions.stream()
                .map(this::toFilePairingProposal)
                .toList();

        final long paired = files.stream()
                .filter(file -> file.getStatus() == TmdbFilePairingProposalStatus.PAIRED)
                .count();

        Log.infof("Proposta de pairing pro anime %d: %d de %d arquivos pareados", anime.anilistId, paired, files.size());

        return new TmdbFilePairingProposalResponse()
                .anilistId(anime.anilistId)
                .tmdbId(anime.tmdbId)
                .files(files);
    }

    private TmdbFilePairingProposal toFilePairingProposal(FilePairingResolution resolution) {
        final TmdbFilePairingProposal proposal = new TmdbFilePairingProposal()
                .fileId(resolution.videoFile().id)
                .fileName(resolution.videoFile().name)
                .status(resolution.status())
                .pairing(resolution.pairing());

        if (resolution.resolvedEpisode() == null) {
            return proposal;
        }

        return proposal
                .seasonNumber(resolution.resolvedEpisode().seasonNumber())
                .episodeNumber(resolution.resolvedEpisode().tmdbEpisode());
    }

}
