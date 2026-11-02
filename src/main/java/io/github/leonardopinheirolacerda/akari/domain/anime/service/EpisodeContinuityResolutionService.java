package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbSeasonSummaryResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationNotConfiguredException;
import io.github.leonardopinheirolacerda.akari.exceptions.ResourceNotFoundException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Resolve o pairing TMDB dos arquivos de vídeo de um anime por continuidade: cada arquivo sem par
 * recebe o episódio seguinte ao do arquivo pareado anterior na pasta — sem gravar nada. Usado
 * pela proposta de pairing do portal ({@link AnimeTmdbProposalService}, estratégia
 * {@code CONTINUITY}).
 *
 * <p>Não consulta o anibridge: depende só do binding TMDB, da estrutura da série no TMDB
 * ({@link TmdbService}, cache-first) e dos pairings já gravados na pasta (lidos uma vez pela
 * proposta e recebidos prontos) — por isso funciona
 * justamente quando o anibridge não mapeia o anime (ex.: especiais pareados à mão na temporada 0).
 *
 * <p>Os arquivos de um anime são os arquivos diretos da pasta dele, então todos estão no mesmo
 * diretório: a continuidade anda pela pasta inteira, na ordem natural dos nomes.
 */
@ApplicationScoped
public class EpisodeContinuityResolutionService {

    @Inject
    VideoFileService videoFileService;

    @Inject
    TmdbService tmdbService;

    @Inject
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    /**
     * Propõe, pra cada arquivo da pasta sem par, o episódio seguinte ao da âncora — o arquivo
     * pareado anterior na ordem natural dos nomes. O arquivo recém-proposto vira a âncora do
     * próximo.
     *
     * <p>Arquivo que já tem par é âncora e sai {@code PAIRED} com o próprio par. Depois do último
     * episódio de uma temporada, segue no primeiro episódio da próxima temporada da série (pelo
     * número, com episódios) — a temporada 0 (especiais) não tem tratamento próprio: uma âncora
     * nela continua dentro dela e, no fim, segue pra temporada 1; e, como vem antes de todas,
     * nunca é a "próxima" de outra. Sem âncora antes do arquivo, sai {@code NO_ANCHOR}; sem
     * episódio seguinte, {@code END_OF_SERIES}.
     *
     * <p>Episódio seguinte que já está gravado em outro arquivo da pasta, ou que já foi proposto
     * antes pra outro arquivo nesta mesma passada, sai {@code EPISODE_IN_USE} — e o arquivo
     * continua sendo a âncora do próximo, pra sequência não quebrar. Quem usa o episódio
     * ({@code usedByFileId}) é preenchido depois, pelo {@link EpisodePairingComparisonService}.
     *
     * @param anime anime com binding TMDB de série
     * @param currentEpisodes pares gravados dos arquivos da pasta, por {@code fileId} — as âncoras
     *                        da continuidade
     * @return um resultado por arquivo da pasta, na ordem natural dos nomes
     * @throws IntegrationException se a ficha ou uma temporada da série não puder ser lida do TMDB
     * @throws IntegrationNotConfiguredException se {@code tmdb.api-key} estiver vazio e a ficha ou
     *                                           a temporada não estiver em cache
     * @throws ResourceNotFoundException se a série vinculada não existir mais no TMDB
     */
    public List<FilePairingResolution> resolve(Anime anime, Map<Integer, ResolvedEpisode> currentEpisodes) {
        final List<VideoFile> videoFiles = videoFileService.listByFolderOrderedByNaturalSortKey(anime.folder.id);

        // Dono de cada episódio já gravado, na ordem natural — o primeiro arquivo que o tem.
        final Map<ResolvedEpisode, Integer> storedOwners = new HashMap<>();

        for (VideoFile videoFile : videoFiles) {
            final ResolvedEpisode currentEpisode = currentEpisodes.get(videoFile.id);

            if (currentEpisode != null) {
                storedOwners.putIfAbsent(currentEpisode, videoFile.id);
            }
        }

        final List<Integer> seasonNumbers = tmdbService.getTvDetails(anime.tmdbId, false)
                .seasons()
                .stream()
                .map(TmdbSeasonSummaryResponse::seasonNumber)
                .filter(Objects::nonNull)
                .sorted()
                .toList();

        final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();
        final Map<ResolvedEpisode, Integer> proposedOwners = new HashMap<>();
        final List<FilePairingResolution> resolutions = new ArrayList<>();

        ResolvedEpisode anchorEpisode = null;
        Integer anchorFileId = null;

        for (VideoFile videoFile : videoFiles) {
            final ResolvedEpisode currentEpisode = currentEpisodes.get(videoFile.id);

            if (currentEpisode != null) {
                final TmdbFilePairingRequest pairing = findEpisode(anime.tmdbId, currentEpisode, episodesBySeason)
                        .map(animeTmdbFilePairingMapper::toPairingRequest)
                        .orElse(null);

                resolutions.add(new FilePairingResolution(
                        videoFile,
                        TmdbFilePairingProposalStatus.PAIRED,
                        currentEpisode,
                        pairing,
                        null
                ));

                anchorEpisode = currentEpisode;
                anchorFileId = videoFile.id;
                continue;
            }

            if (anchorEpisode == null) {
                resolutions.add(FilePairingResolution.unresolved(videoFile, TmdbFilePairingProposalStatus.NO_ANCHOR));
                continue;
            }

            final Optional<TmdbEpisodeResponse> nextEpisode = findNextEpisode(
                    anime.tmdbId,
                    anchorEpisode,
                    seasonNumbers,
                    episodesBySeason
            );

            if (nextEpisode.isEmpty()) {
                resolutions.add(FilePairingResolution.unresolved(videoFile, TmdbFilePairingProposalStatus.END_OF_SERIES));
                continue;
            }

            final ResolvedEpisode proposedEpisode = new ResolvedEpisode(
                    nextEpisode.get().seasonNumber(),
                    nextEpisode.get().episodeNumber()
            );

            final Integer usedByFileId = Optional.ofNullable(storedOwners.get(proposedEpisode))
                    .orElse(proposedOwners.get(proposedEpisode));

            final TmdbFilePairingProposalStatus status = usedByFileId == null
                    ? TmdbFilePairingProposalStatus.PAIRED
                    : TmdbFilePairingProposalStatus.EPISODE_IN_USE;

            resolutions.add(new FilePairingResolution(
                    videoFile,
                    status,
                    proposedEpisode,
                    animeTmdbFilePairingMapper.toPairingRequest(nextEpisode.get()),
                    anchorFileId
            ));

            proposedOwners.putIfAbsent(proposedEpisode, videoFile.id);

            anchorEpisode = proposedEpisode;
            anchorFileId = videoFile.id;
        }

        return resolutions;
    }

    /**
     * Episódio seguinte ao da âncora: o próximo da mesma temporada; no fim dela, o primeiro da
     * próxima temporada com episódios, na ordem dos números de temporada.
     */
    private Optional<TmdbEpisodeResponse> findNextEpisode(Integer tmdbId,
                                                          ResolvedEpisode anchorEpisode,
                                                          List<Integer> seasonNumbers,
                                                          Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason) {

        final Optional<TmdbEpisodeResponse> nextInSeason = episodesOf(tmdbId, anchorEpisode.seasonNumber(), episodesBySeason)
                .stream()
                .filter(episode -> episode.episodeNumber() > anchorEpisode.tmdbEpisode())
                .min(Comparator.comparing(TmdbEpisodeResponse::episodeNumber));

        if (nextInSeason.isPresent()) {
            return nextInSeason;
        }

        return seasonNumbers.stream()
                .filter(seasonNumber -> seasonNumber > anchorEpisode.seasonNumber())
                .map(seasonNumber -> episodesOf(tmdbId, seasonNumber, episodesBySeason)
                        .stream()
                        .min(Comparator.comparing(TmdbEpisodeResponse::episodeNumber)))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private Optional<TmdbEpisodeResponse> findEpisode(Integer tmdbId,
                                                      ResolvedEpisode resolvedEpisode,
                                                      Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason) {

        return episodesOf(tmdbId, resolvedEpisode.seasonNumber(), episodesBySeason)
                .stream()
                .filter(episode -> episode.episodeNumber().equals(resolvedEpisode.tmdbEpisode()))
                .findFirst();
    }

    /**
     * Episódios de uma temporada, cacheados por chamada. Temporada que não existe no TMDB (ex.:
     * par gravado apontando pra uma temporada removida) conta como vazia — o resto da série ainda
     * pode ser usado; outras falhas do TMDB propagam.
     */
    private List<TmdbEpisodeResponse> episodesOf(Integer tmdbId,
                                                 Integer seasonNumber,
                                                 Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason) {

        return episodesBySeason.computeIfAbsent(
                seasonNumber,
                number -> tmdbService.getSeasonEpisodes(tmdbId, number)
        );
    }

}
