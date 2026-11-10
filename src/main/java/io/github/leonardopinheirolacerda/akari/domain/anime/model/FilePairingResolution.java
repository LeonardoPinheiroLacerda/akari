package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;

/**
 * Resultado da resolução de pairing TMDB de um arquivo de vídeo, calculado pelo
 * {@code EpisodePairingResolutionService} (estratégia anibridge) ou pelo
 * {@code EpisodeContinuityResolutionService} (estratégia de continuidade), ambos pra proposta de
 * pairing do portal — o anibridge também pro pairing automático da ingestão
 * ({@code AnimeTmdbFilePairingFillService}).
 * Na proposta, o {@code EpisodePairingComparisonService} compara cada resultado com o par gravado
 * do arquivo.
 *
 * @param videoFile arquivo resolvido
 * @param status pareado, ou o motivo de não ter pairing
 * @param resolvedEpisode temporada/episódio do TMDB resolvidos; {@code null} quando a estratégia
 *                        não chegou a um episódio (preenchido em {@code PAIRED} e, no anibridge,
 *                        em {@code NOT_FOUND_IN_TMDB} e {@code OUT_OF_AIR_WINDOW})
 * @param pairing payload do pairing com a metadata do episódio no TMDB; só preenchido em
 *                {@code PAIRED} — na continuidade, pode vir {@code null} no arquivo que já tinha
 *                par, se o episódio gravado não existir mais no TMDB
 * @param basedOnFileId arquivo âncora de onde a continuidade partiu; {@code null} no anibridge e
 *                      no arquivo que já tinha par
 */
public record FilePairingResolution(
        VideoFile videoFile,
        TmdbFilePairingProposalStatus status,
        ResolvedEpisode resolvedEpisode,
        TmdbFilePairingRequest pairing,
        Integer basedOnFileId
) {

    /**
     * Resultado de um arquivo que a estratégia não resolveu pra nenhum episódio — só o status.
     *
     * @param videoFile arquivo resolvido
     * @param status motivo de não ter pairing
     * @return o resultado, sem episódio, pairing nem arquivos relacionados
     */
    public static FilePairingResolution unresolved(VideoFile videoFile, TmdbFilePairingProposalStatus status) {
        return new FilePairingResolution(videoFile, status, null, null, null);
    }

}
