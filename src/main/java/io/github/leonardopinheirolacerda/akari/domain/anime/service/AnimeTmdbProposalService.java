package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingDetails;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbBindingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposal;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalRequest;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalResponse;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingStrategy;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbMediaType;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
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
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Propostas de binding TMDB (pelo anibridge) e de pairing de episódios (pelo anibridge ou por
 * continuidade) pra um anime — só consulta, nada é gravado.
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

    @Inject
    EpisodeContinuityResolutionService episodeContinuityResolutionService;

    @Inject
    EpisodePairingComparisonService episodePairingComparisonService;

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
     * Propõe o pairing TMDB de cada arquivo de vídeo da pasta do anime, pela estratégia pedida —
     * sem gravar nada.
     *
     * <p>{@code ANIBRIDGE} (padrão, também quando {@code strategy} vem {@code null}) usa a mesma
     * resolução do pairing automático da ingestão ({@link EpisodePairingResolutionService});
     * {@code CONTINUITY} propõe o episódio seguinte ao do arquivo pareado anterior, sem consultar
     * o anibridge ({@link EpisodeContinuityResolutionService}). Os pares gravados da pasta são
     * lidos uma vez e servem às âncoras da continuidade e à comparação
     * ({@link EpisodePairingComparisonService}), que monta par atual, {@code change},
     * {@code usedByFileId} e a pré-seleção de cada arquivo.
     *
     * @param request anime cujos arquivos devem receber a proposta, e a estratégia
     * @return a série vinculada e a proposta de cada arquivo da pasta, na ordem natural dos nomes
     * @throws ResourceNotFoundException se o anime não existir ou não tiver binding TMDB, ou se a
     *                                     série vinculada não existir mais no TMDB
     *                                     ({@code CONTINUITY})
     * @throws BusinessRuleException se o binding TMDB for de filme
     * @throws IntegrationException se a estrutura da série não puder ser lida do TMDB
     *                              ({@code CONTINUITY})
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a
     *                                           estrutura da série não estiver em cache
     */
    public TmdbFilePairingProposalResponse proposeTmdbFilePairing(TmdbFilePairingProposalRequest request) {
        final Anime anime = animeTmdbService.findTvBoundAnimeOrThrow(request.getAnilistId());

        final TmdbFilePairingStrategy strategy = Objects.requireNonNullElse(
                request.getStrategy(),
                TmdbFilePairingStrategy.ANIBRIDGE
        );

        final Map<Integer, ResolvedEpisode> currentEpisodes = episodePairingComparisonService.findCurrentEpisodes(anime.folder.id);

        final List<FilePairingResolution> resolutions = switch (strategy) {
            case ANIBRIDGE -> episodePairingResolutionService.resolve(anime, anime.folder.id, anime.folder.name);
            case CONTINUITY -> episodeContinuityResolutionService.resolve(anime, currentEpisodes);
        };

        final List<TmdbFilePairingProposal> files = episodePairingComparisonService.compare(resolutions, currentEpisodes);

        final long paired = files.stream()
                .filter(file -> file.getStatus() == TmdbFilePairingProposalStatus.PAIRED)
                .count();

        Log.infof(
                "Proposta de pairing %s pro anime %d: %d de %d arquivos pareados",
                strategy,
                anime.anilistId,
                paired,
                files.size()
        );

        return new TmdbFilePairingProposalResponse()
                .anilistId(anime.anilistId)
                .tmdbId(anime.tmdbId)
                .files(files);
    }

}
