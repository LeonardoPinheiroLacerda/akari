package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.MappingSegment;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.service.AnibridgeResolutionService;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.EpisodeMapping;

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Conversão, pelo anibridge, do número de episódio dos arquivos de uma pasta pra
 * temporada/episódio do TMDB — preparada uma vez por pasta pelo {@link AnibridgeEpisodeMapper},
 * com os segments da série vinculada e a numeração absoluta da pasta já decididos.
 *
 * <p>Pasta cujo anime não tem mapeamento utilizável sai com o mesmo motivo em todos os arquivos
 * ({@code NO_MAPPING} ou {@code CONFLICTING_MAPPING}).
 */
public final class AnibridgeFolderMapping {

    private final AnibridgeResolutionService anibridgeResolutionService;

    private final TmdbFilePairingProposalStatus unusableStatus;

    private final List<MappingSegment> safeSegments;

    private final List<MappingSegment> offsetSegments;

    private final List<Integer> offsetCandidates;

    private final OptionalInt folderOffset;

    private final boolean singleEpisodeFolder;

    private AnibridgeFolderMapping(AnibridgeResolutionService anibridgeResolutionService,
                                   TmdbFilePairingProposalStatus unusableStatus,
                                   List<MappingSegment> safeSegments,
                                   List<MappingSegment> offsetSegments,
                                   List<Integer> offsetCandidates,
                                   OptionalInt folderOffset,
                                   boolean singleEpisodeFolder) {

        this.anibridgeResolutionService = anibridgeResolutionService;
        this.unusableStatus = unusableStatus;
        this.safeSegments = safeSegments;
        this.offsetSegments = offsetSegments;
        this.offsetCandidates = offsetCandidates;
        this.folderOffset = folderOffset;
        this.singleEpisodeFolder = singleEpisodeFolder;
    }

    /**
     * Pasta sem mapeamento utilizável — todo arquivo sai com o mesmo motivo.
     *
     * @param status {@code NO_MAPPING} ou {@code CONFLICTING_MAPPING}
     * @return a conversão que não resolve nenhum arquivo
     */
    static AnibridgeFolderMapping unusable(TmdbFilePairingProposalStatus status) {
        return new AnibridgeFolderMapping(
                null,
                status,
                List.of(),
                null,
                List.of(),
                OptionalInt.empty(),
                false
        );
    }

    /**
     * Pasta com mapeamento utilizável.
     *
     * @param anibridgeResolutionService conversão de um número pelos segments
     * @param safeSegments segments seguros da série vinculada
     * @param offsetSegments segments que cobrem a temporada isolada, onde a numeração absoluta
     *                       pode ser aplicada; {@code null} se não há
     * @param offsetCandidates offsets da cadeia de prequels, do mais próximo ao mais distante
     * @param folderOffset offset da numeração absoluta da pasta inteira; vazio se a pasta não
     *                     usa numeração absoluta
     * @param singleEpisodeFolder pasta de 1 arquivo com anime de 1 episódio
     * @return a conversão da pasta
     */
    static AnibridgeFolderMapping usable(AnibridgeResolutionService anibridgeResolutionService,
                                         List<MappingSegment> safeSegments,
                                         List<MappingSegment> offsetSegments,
                                         List<Integer> offsetCandidates,
                                         OptionalInt folderOffset,
                                         boolean singleEpisodeFolder) {

        return new AnibridgeFolderMapping(
                anibridgeResolutionService,
                null,
                safeSegments,
                offsetSegments,
                offsetCandidates,
                folderOffset,
                singleEpisodeFolder
        );
    }

    /**
     * Converte o número de episódio de um arquivo da pasta.
     *
     * <p>Com numeração absoluta na pasta, aplica o offset dela. Senão, converte o número direto e,
     * sem resultado, tenta os offsets da cadeia de prequels, do mais próximo ao mais distante.
     * Numa pasta de 1 arquivo com anime de 1 episódio, o episódio 1 é o último recurso.
     *
     * @param episodeNumber número extraído do nome do arquivo
     * @return o episódio do TMDB, ou o motivo de não ter resolvido: {@code NO_EPISODE_NUMBER},
     *         {@code NOT_MAPPED}, {@code AMBIGUOUS_ABSOLUTE_NUMBERING} (numeração absoluta sem
     *         segments seguros pra aplicar o offset), ou o motivo da pasta sem mapeamento
     */
    public EpisodeMapping map(OptionalInt episodeNumber) {
        if (unusableStatus != null) {
            return EpisodeMapping.unresolved(unusableStatus);
        }

        Optional<ResolvedEpisode> resolved = Optional.empty();
        boolean offsetNeededButIneligible = false;

        if (episodeNumber.isPresent() && folderOffset.isPresent()) {
            resolved = anibridgeResolutionService.resolveEpisode(
                    offsetSegments,
                    episodeNumber.getAsInt() - folderOffset.getAsInt()
            );
        } else if (episodeNumber.isPresent()) {
            resolved = anibridgeResolutionService.resolveEpisode(
                    safeSegments,
                    episodeNumber.getAsInt()
            );

            if (resolved.isEmpty() && !offsetCandidates.isEmpty()) {
                if (offsetSegments != null) {
                    resolved = resolveWithOffsets(episodeNumber.getAsInt());
                } else {
                    offsetNeededButIneligible = true;
                }
            }
        }

        if (resolved.isEmpty() && singleEpisodeFolder) {
            resolved = anibridgeResolutionService.resolveEpisode(safeSegments, 1);
        }

        if (resolved.isEmpty()) {
            final TmdbFilePairingProposalStatus status = unresolvedStatus(
                    episodeNumber,
                    offsetNeededButIneligible
            );

            return EpisodeMapping.unresolved(status);
        }

        return EpisodeMapping.resolved(resolved.get());
    }

    /**
     * Tenta resolver o número pela numeração absoluta, um offset da cadeia de prequels por vez,
     * parando no primeiro que o anibridge resolver.
     */
    private Optional<ResolvedEpisode> resolveWithOffsets(Integer episodeNumber) {
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
    private static TmdbFilePairingProposalStatus unresolvedStatus(
            OptionalInt episodeNumber,
            boolean offsetNeededButIneligible) {

        if (offsetNeededButIneligible) {
            return TmdbFilePairingProposalStatus.AMBIGUOUS_ABSOLUTE_NUMBERING;
        }

        if (episodeNumber.isEmpty()) {
            return TmdbFilePairingProposalStatus.NO_EPISODE_NUMBER;
        }

        return TmdbFilePairingProposalStatus.NOT_MAPPED;
    }

}
