package io.github.leonardopinheirolacerda.akari.domain.anime.service;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.TmdbMappingCandidate;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anime.mapper.AnimeTmdbFilePairingMapper;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.Anime;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.FilePairingResolution;
import io.github.leonardopinheirolacerda.akari.domain.ingestion.extractor.EpisodeNumberExtractor;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.client.dtos.TmdbEpisodeResponse;
import io.github.leonardopinheirolacerda.akari.domain.tmdb.service.TmdbService;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;
import io.github.leonardopinheirolacerda.akari.domain.videofile.service.VideoFileService;
import io.github.leonardopinheirolacerda.akari.exceptions.IntegrationException;
import io.github.leonardopinheirolacerda.akari.utils.RetryUtils;
import io.quarkus.logging.Log;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Resolve o pairing TMDB dos arquivos de vídeo de um anime vinculado a uma série do TMDB, sem
 * gravar nada — a mesma resolução serve ao pairing automático da ingestão
 * ({@code FolderAutoBindService}, que grava e notifica) e à proposta de pairing do portal
 * ({@link AnimeTmdbProposalService}, que só devolve).
 *
 * <p>O número do episódio sai do nome do arquivo ({@link EpisodeNumberExtractor}), e o anibridge
 * converte pra temporada/episódio do TMDB — só pelos segments que apontam pra série vinculada,
 * pra nunca aplicar a numeração de um título sobre outro. Cada episódio resolvido é confirmado no
 * TMDB, de onde sai a metadata do pairing.
 */
@ApplicationScoped
public class EpisodePairingResolutionService {

    @Inject
    AnibridgeResolutionService anibridgeResolutionService;

    @Inject
    AbsoluteEpisodeOffsetService absoluteEpisodeOffsetService;

    @Inject
    VideoFileService videoFileService;

    @Inject
    TmdbService tmdbService;

    @Inject
    AnimeTmdbFilePairingMapper animeTmdbFilePairingMapper;

    /**
     * Resolve o pairing de cada arquivo de vídeo da pasta do anime.
     *
     * <p>Além do número extraído do nome, tenta o fallback de numeração absoluta (offsets da
     * cadeia de prequels, ver {@link AbsoluteEpisodeOffsetService}) e, numa pasta de 1 arquivo
     * com anime de 1 episódio, o episódio 1. Sem mapeamento do anibridge pra série vinculada, ou
     * com mapeamento conflitante, todos os arquivos saem com {@code NO_MAPPING} ou
     * {@code CONFLICTING_MAPPING}.
     *
     * @param anime anime com binding TMDB de série
     * @param folderId pasta do anime — passada à parte pra não depender do lazy load de
     *                 {@code anime.folder} (a ingestão roda fora de request)
     * @param folderName nome da pasta, só pra log
     * @return um resultado por arquivo da pasta, na ordem natural dos nomes
     */
    public List<FilePairingResolution> resolve(Anime anime, Integer folderId, String folderName) {
        final List<VideoFile> videoFiles = videoFileService.listByFolderOrderedByNaturalSortKey(folderId);

        final Optional<TmdbMappingCandidate> candidate = anibridgeResolutionService.resolveCandidateFor(
                anime.anilistId,
                anime.malId,
                anime.tmdbId,
                anime.tmdbMediaType
        );

        if (candidate.isEmpty()) {
            return withStatus(videoFiles, TmdbFilePairingProposalStatus.NO_MAPPING);
        }

        final Optional<List<MappingSegment>> safeSegments = anibridgeResolutionService.resolveSafeSegments(candidate.get());

        if (safeSegments.isEmpty()) {
            return withStatus(videoFiles, TmdbFilePairingProposalStatus.CONFLICTING_MAPPING);
        }

        final Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason = new HashMap<>();

        // Pasta de 1 arquivo + anime de 1 episódio: aquele arquivo só pode ser o episódio 1 —
        // usado como último recurso, depois de qualquer outra tentativa falhar. Cobre tanto o
        // arquivo sem número reconhecível quanto o caso comum de OVA/special numerado pela
        // contagem absoluta da série principal (ex.: "Clannad After Story - 25 OVA"), que não
        // bate com o range local (1-1) do segment do anibridge pra esse special.
        final boolean singleEpisodeFolder = videoFiles.size() == 1
                && anime.episodes != null
                && anime.episodes == 1;

        final List<Integer> absoluteOffsetCandidates = absoluteEpisodeOffsetService.computeOffsetCandidates(anime.anilistId);

        final Optional<List<MappingSegment>> offsetSegments = absoluteOffsetCandidates.isEmpty()
                ? Optional.empty()
                : anibridgeResolutionService.restrictToFullSeasonCoverage(safeSegments.get(), anime.episodes);

        final List<FilePairingResolution> resolutions = new ArrayList<>();

        for (VideoFile videoFile : videoFiles) {
            final OptionalInt episodeNumber = EpisodeNumberExtractor.extract(videoFile.name);

            Optional<ResolvedEpisode> resolved = Optional.empty();
            boolean offsetNeededButIneligible = false;

            if (episodeNumber.isPresent()) {
                resolved = anibridgeResolutionService.resolveEpisode(
                        safeSegments.get(),
                        episodeNumber.getAsInt()
                );

                if (resolved.isEmpty() && !absoluteOffsetCandidates.isEmpty()) {
                    if (offsetSegments.isPresent()) {
                        resolved = resolveWithOffsets(
                                offsetSegments.get(),
                                absoluteOffsetCandidates,
                                episodeNumber.getAsInt()
                        );
                    } else {
                        offsetNeededButIneligible = true;
                    }
                }
            }

            if (resolved.isEmpty() && singleEpisodeFolder) {
                resolved = anibridgeResolutionService.resolveEpisode(safeSegments.get(), 1);
            }

            if (resolved.isEmpty()) {
                final TmdbFilePairingProposalStatus status = unresolvedStatus(episodeNumber, offsetNeededButIneligible);
                resolutions.add(FilePairingResolution.unresolved(videoFile, status));
                continue;
            }

            final Optional<TmdbEpisodeResponse> episode = resolveEpisode(
                    anime.tmdbId,
                    resolved.get(),
                    episodesBySeason,
                    folderId,
                    folderName
            );

            if (episode.isEmpty()) {
                resolutions.add(new FilePairingResolution(
                        videoFile,
                        TmdbFilePairingProposalStatus.NOT_FOUND_IN_TMDB,
                        resolved.get(),
                        null,
                        null
                ));
                continue;
            }

            final TmdbFilePairingRequest pairing = animeTmdbFilePairingMapper.toPairingRequest(episode.get());

            resolutions.add(new FilePairingResolution(
                    videoFile,
                    TmdbFilePairingProposalStatus.PAIRED,
                    resolved.get(),
                    pairing,
                    null
            ));
        }

        return resolutions;
    }

    private List<FilePairingResolution> withStatus(List<VideoFile> videoFiles, TmdbFilePairingProposalStatus status) {
        return videoFiles.stream()
                .map(videoFile -> FilePairingResolution.unresolved(videoFile, status))
                .toList();
    }

    /**
     * Tenta resolver o número pela numeração absoluta, um offset da cadeia de prequels por vez,
     * parando no primeiro que o anibridge resolver.
     */
    private Optional<ResolvedEpisode> resolveWithOffsets(List<MappingSegment> offsetSegments,
                                                         List<Integer> offsetCandidates,
                                                         Integer episodeNumber) {

        return offsetCandidates.stream()
                .map(offsetCandidate -> anibridgeResolutionService.resolveEpisode(
                        offsetSegments,
                        episodeNumber - offsetCandidate
                ))
                .flatMap(Optional::stream)
                .findFirst();
    }

    /**
     * Motivo de um arquivo que o anibridge não resolveu: numeração absoluta sem cobertura segura
     * pra aplicar o offset, nome sem número reconhecível, ou número fora do mapeamento.
     */
    private TmdbFilePairingProposalStatus unresolvedStatus(OptionalInt episodeNumber, boolean offsetNeededButIneligible) {
        if (offsetNeededButIneligible) {
            return TmdbFilePairingProposalStatus.AMBIGUOUS_ABSOLUTE_NUMBERING;
        }

        if (episodeNumber.isEmpty()) {
            return TmdbFilePairingProposalStatus.NO_EPISODE_NUMBER;
        }

        return TmdbFilePairingProposalStatus.NOT_MAPPED;
    }

    /**
     * Busca no TMDB o episódio resolvido pelo anibridge, de onde sai a metadata do pairing
     * (thumbnail, nome, sinopse etc.). Cacheia a temporada inteira por pasta — evita uma chamada
     * por arquivo quando vários episódios caem na mesma temporada. Vazio se a temporada não
     * existir no TMDB (o anibridge pode estar dessincronizado dele), se a busca falhar ou se o
     * episódio não estiver na temporada — nesses casos o arquivo sai como
     * {@code NOT_FOUND_IN_TMDB}, sem pairing, pra aparecer como pendente em vez de pareado sem
     * metadata.
     */
    private Optional<TmdbEpisodeResponse> resolveEpisode(Integer tmdbId,
                                                         ResolvedEpisode resolved,
                                                         Map<Integer, List<TmdbEpisodeResponse>> episodesBySeason,
                                                         Integer folderId,
                                                         String folderName) {

        if (resolved.seasonNumber() == null) {
            return Optional.empty();
        }

        final List<TmdbEpisodeResponse> episodes = episodesBySeason.computeIfAbsent(resolved.seasonNumber(), seasonNumber -> {
            try {
                return RetryUtils.withRateLimitRetry(() -> tmdbService.getSeasonEpisodes(tmdbId, seasonNumber));
            } catch (IntegrationException e) {
                Log.warnf(e, "Busca da temporada %d no TMDB falhou pra pasta %d (%s)", seasonNumber, folderId, folderName);
                return List.of();
            }
        });

        return episodes.stream()
                .filter(episode -> episode.episodeNumber().equals(resolved.tmdbEpisode()))
                .findFirst();
    }

}
