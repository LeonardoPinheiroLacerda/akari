package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingRequest;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.videofile.model.VideoFile;

/**
 * Resultado da resolução de pairing TMDB de um arquivo de vídeo, calculado pelo
 * {@code EpisodePairingResolutionService} — usado tanto pelo pairing automático da ingestão
 * (que grava o {@code pairing}) quanto pela proposta de pairing do portal (que só devolve).
 *
 * @param videoFile arquivo resolvido
 * @param status pareado, ou o motivo de não ter pairing
 * @param resolvedEpisode temporada/episódio do TMDB resolvidos pelo anibridge; {@code null}
 *                        quando o anibridge não resolveu o arquivo (preenchido em {@code PAIRED}
 *                        e {@code NOT_FOUND_IN_TMDB})
 * @param pairing payload do pairing com a metadata do episódio no TMDB; só preenchido em
 *                {@code PAIRED}
 */
public record FilePairingResolution(
        VideoFile videoFile,
        TmdbFilePairingProposalStatus status,
        ResolvedEpisode resolvedEpisode,
        TmdbFilePairingRequest pairing
) {
}
