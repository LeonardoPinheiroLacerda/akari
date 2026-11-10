package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;

/**
 * Resultado da conversão do número de episódio de um arquivo pra temporada/episódio do TMDB, na
 * resolução do pairing (ver {@code AnibridgeFolderMapping}) — ou o episódio, ou o motivo de não
 * ter chegado a um.
 *
 * @param episode temporada/episódio do TMDB; {@code null} quando a conversão não resolveu
 * @param status motivo de não ter resolvido ({@code NO_EPISODE_NUMBER}, {@code NOT_MAPPED}...);
 *               {@code null} quando resolveu
 */
public record EpisodeMapping(ResolvedEpisode episode, TmdbFilePairingProposalStatus status) {

    /**
     * Conversão que chegou a um episódio do TMDB.
     *
     * @param episode temporada/episódio do TMDB
     * @return o resultado, sem motivo
     */
    public static EpisodeMapping resolved(ResolvedEpisode episode) {
        return new EpisodeMapping(episode, null);
    }

    /**
     * Conversão que não chegou a um episódio do TMDB.
     *
     * @param status motivo de não ter resolvido
     * @return o resultado, sem episódio
     */
    public static EpisodeMapping unresolved(TmdbFilePairingProposalStatus status) {
        return new EpisodeMapping(null, status);
    }

}
