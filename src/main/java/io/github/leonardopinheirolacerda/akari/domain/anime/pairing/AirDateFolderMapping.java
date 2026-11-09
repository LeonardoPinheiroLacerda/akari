package io.github.leonardopinheirolacerda.akari.domain.anime.pairing;

import io.github.leonardopinheirolacerda.akari.api.dto.TmdbFilePairingProposalStatus;
import io.github.leonardopinheirolacerda.akari.domain.anibridge.model.ResolvedEpisode;
import io.github.leonardopinheirolacerda.akari.domain.anime.model.EpisodeMapping;

import java.util.List;
import java.util.OptionalInt;

/**
 * Conversão pelo período de exibição do número de episódio dos arquivos de uma pasta pra
 * temporada/episódio do TMDB — preparada uma vez por pasta pelo {@link AirDateEpisodeMapper}:
 * os episódios do TMDB exibidos no período do anime, em ordem de exibição, onde o arquivo de
 * número {@code N} fica com o {@code N}-ésimo.
 */
public final class AirDateFolderMapping {

    private final List<ResolvedEpisode> airedEpisodes;

    private AirDateFolderMapping(List<ResolvedEpisode> airedEpisodes) {
        this.airedEpisodes = airedEpisodes;
    }

    /**
     * Conversão que não resolve nenhum arquivo — o período do anime não é conhecido ou não bate
     * com os episódios do TMDB.
     *
     * @return a conversão vazia
     */
    static AirDateFolderMapping empty() {
        return new AirDateFolderMapping(List.of());
    }

    /**
     * Conversão pelos episódios exibidos no período do anime.
     *
     * @param airedEpisodes episódios do TMDB exibidos no período, em ordem de exibição
     * @return a conversão da pasta
     */
    static AirDateFolderMapping of(List<ResolvedEpisode> airedEpisodes) {
        return new AirDateFolderMapping(airedEpisodes);
    }

    /**
     * Converte o número de episódio de um arquivo da pasta na posição dele entre os episódios
     * exibidos no período.
     *
     * @param episodeNumber número extraído do nome do arquivo
     * @return o {@code N}-ésimo episódio exibido no período; {@code NOT_MAPPED} sem número, ou
     *         com número fora da quantidade de episódios exibidos
     */
    public EpisodeMapping map(OptionalInt episodeNumber) {
        if (episodeNumber.isEmpty()
                || episodeNumber.getAsInt() < 1
                || episodeNumber.getAsInt() > airedEpisodes.size()) {

            return EpisodeMapping.unresolved(TmdbFilePairingProposalStatus.NOT_MAPPED);
        }

        return EpisodeMapping.resolved(airedEpisodes.get(episodeNumber.getAsInt() - 1));
    }

}
