package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingChange;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposal;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.TmdbFilePairing;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Compara a resolução de pairing de cada arquivo com o par gravado nele e monta a proposta do
 * portal: par atual, {@code change}, quem já usa o episódio proposto e a pré-seleção.
 *
 * <p>Não sabe qual estratégia gerou as resoluções ({@link EpisodePairingResolutionService} ou
 * {@link EpisodeContinuityResolutionService}) — a regra é a mesma pras duas e pra qualquer
 * temporada.
 */
@ApplicationScoped
public class EpisodePairingComparisonService {

    private static final Set<TmdbFilePairingProposalStatus> STATUSES_WITH_PROPOSAL = Set.of(
            TmdbFilePairingProposalStatus.PAIRED,
            TmdbFilePairingProposalStatus.EPISODE_IN_USE
    );

    /**
     * Lê os pares gravados dos arquivos da pasta — só os que têm temporada e episódio
     * preenchidos, que é o que conta como par pra comparação e pra âncora da continuidade.
     *
     * <p>{@code @Transactional} mesmo sendo leitura, pra funcionar também chamado de contextos
     * sem transação ambiente.
     *
     * @param folderId pasta do anime
     * @return temporada/episódio gravados, por {@code fileId}; arquivos sem par não aparecem
     */
    @Transactional
    public Map<Integer, ResolvedEpisode> findCurrentEpisodes(Integer folderId) {
        return TmdbFilePairing.findByFolderId(folderId)
                .stream()
                .filter(pairing -> pairing.seasonNumber != null && pairing.episodeNumber != null)
                .collect(Collectors.toMap(
                        pairing -> pairing.fileId,
                        pairing -> new ResolvedEpisode(pairing.seasonNumber, pairing.episodeNumber)
                ));
    }

    /**
     * Monta a proposta de cada arquivo a partir da resolução, comparando com o par gravado.
     *
     * <p>Só {@code PAIRED} e {@code EPISODE_IN_USE} são proposta: recebem {@code change}
     * ({@code NEW} sem par gravado, {@code SAME} igual, {@code REPLACE} diferente) e
     * {@code usedByFileId} — o primeiro outro arquivo (na ordem das resoluções) com esse par
     * gravado, senão um que recebeu a mesma proposta antes. Vem pré-selecionada só a proposta {@code NEW} sem uso por outro arquivo.
     *
     * @param resolutions resolução de cada arquivo da pasta, na ordem natural dos nomes
     * @param currentEpisodes pares gravados por {@code fileId} (ver {@link #findCurrentEpisodes})
     * @return a proposta de cada arquivo, na mesma ordem das resoluções
     */
    public List<TmdbFilePairingProposal> compare(List<FilePairingResolution> resolutions,
                                                 Map<Integer, ResolvedEpisode> currentEpisodes) {

        final Map<ResolvedEpisode, Integer> proposedOwners = new HashMap<>();
        final List<TmdbFilePairingProposal> proposals = new ArrayList<>();

        for (FilePairingResolution resolution : resolutions) {
            proposals.add(toProposal(resolution, resolutions, currentEpisodes, proposedOwners));
        }

        return proposals;
    }

    private TmdbFilePairingProposal toProposal(FilePairingResolution resolution,
                                               List<FilePairingResolution> resolutions,
                                               Map<Integer, ResolvedEpisode> currentEpisodes,
                                               Map<ResolvedEpisode, Integer> proposedOwners) {

        final Integer fileId = resolution.videoFile().id;
        final ResolvedEpisode currentEpisode = currentEpisodes.get(fileId);
        final ResolvedEpisode proposedEpisode = resolution.resolvedEpisode();

        final TmdbFilePairingProposal proposal = new TmdbFilePairingProposal()
                .fileId(fileId)
                .fileName(resolution.videoFile().name)
                .status(resolution.status())
                .pairing(resolution.pairing())
                .basedOnFileId(resolution.basedOnFileId())
                .selectedByDefault(false);

        if (currentEpisode != null) {
            proposal.currentSeasonNumber(currentEpisode.seasonNumber())
                    .currentEpisodeNumber(currentEpisode.tmdbEpisode());
        }

        if (proposedEpisode != null) {
            proposal.seasonNumber(proposedEpisode.seasonNumber())
                    .episodeNumber(proposedEpisode.tmdbEpisode());
        }

        // NOT_FOUND_IN_TMDB e OUT_OF_AIR_WINDOW (anibridge) têm episódio, mas não são proposta.
        if (!STATUSES_WITH_PROPOSAL.contains(resolution.status())) {
            return proposal;
        }

        final TmdbFilePairingChange change = changeOf(currentEpisode, proposedEpisode);
        final Integer usedByFileId = findUsedByFileId(fileId, proposedEpisode, resolutions, currentEpisodes, proposedOwners);

        proposedOwners.putIfAbsent(proposedEpisode, fileId);

        return proposal
                .change(change)
                .usedByFileId(usedByFileId)
                .selectedByDefault(change == TmdbFilePairingChange.NEW && usedByFileId == null);
    }

    private TmdbFilePairingChange changeOf(ResolvedEpisode currentEpisode, ResolvedEpisode proposedEpisode) {
        if (currentEpisode == null) {
            return TmdbFilePairingChange.NEW;
        }

        if (currentEpisode.equals(proposedEpisode)) {
            return TmdbFilePairingChange.SAME;
        }

        return TmdbFilePairingChange.REPLACE;
    }

    /**
     * Outro arquivo que já usa o episódio proposto: primeiro um que tenha esse par gravado (o
     * primeiro na ordem das resoluções), senão um que recebeu a mesma proposta antes nesta rodada;
     * {@code null} se nenhum.
     */
    private Integer findUsedByFileId(Integer fileId,
                                     ResolvedEpisode proposedEpisode,
                                     List<FilePairingResolution> resolutions,
                                     Map<Integer, ResolvedEpisode> currentEpisodes,
                                     Map<ResolvedEpisode, Integer> proposedOwners) {

        return resolutions.stream()
                .map(resolution -> resolution.videoFile().id)
                .filter(otherFileId -> !otherFileId.equals(fileId))
                .filter(otherFileId -> proposedEpisode.equals(currentEpisodes.get(otherFileId)))
                .findFirst()
                .orElse(proposedOwners.get(proposedEpisode));
    }

}
