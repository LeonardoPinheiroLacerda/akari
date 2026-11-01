package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;

/**
 * Resultado da resolução de pairing TMDB de um arquivo de vídeo, calculado pelo
 * {@code EpisodePairingResolutionService} (estratégia anibridge — usado tanto pelo pairing
 * automático da ingestão, que grava o {@code pairing}, quanto pela proposta de pairing do portal)
 * ou pelo {@code EpisodeContinuityResolutionService} (estratégia de continuidade, só proposta).
 * Na proposta, o {@code EpisodePairingComparisonService} compara cada resultado com o par gravado
 * do arquivo.
 *
 * @param videoFile arquivo resolvido
 * @param status pareado, ou o motivo de não ter pairing
 * @param resolvedEpisode temporada/episódio do TMDB resolvidos; {@code null} quando a estratégia
 *                        não chegou a um episódio (preenchido em {@code PAIRED} e, no anibridge,
 *                        em {@code NOT_FOUND_IN_TMDB})
 * @param pairing payload do pairing com a metadata do episódio no TMDB; só preenchido em
 *                {@code PAIRED} — na continuidade, pode vir {@code null} no arquivo que já tinha
 *                par, se o episódio gravado não existir mais no TMDB
 * @param basedOnFileId arquivo âncora de onde a continuidade partiu; {@code null} no anibridge e
 *                      no arquivo que já tinha par
 * @param usedByFileId outro arquivo que já usa o episódio proposto, quando a própria estratégia
 *                     detecta isso (continuidade, em {@code EPISODE_IN_USE}); {@code null} quando
 *                     a estratégia não verifica (anibridge) — aí a comparação com os pares
 *                     gravados é que preenche
 */
public record FilePairingResolution(
        VideoFile videoFile,
        TmdbFilePairingProposalStatus status,
        ResolvedEpisode resolvedEpisode,
        TmdbFilePairingRequest pairing,
        Integer basedOnFileId,
        Integer usedByFileId
) {
}
