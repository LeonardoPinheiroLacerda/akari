package io.github.leonardopinheirolacerda.akari.domain.anime.model;

import java.util.List;

/**
 * Resultado do preenchimento automático de pairing dos arquivos de uma pasta (ver
 * {@code AnimeTmdbFilePairingFillService#fillPairings}).
 *
 * @param paired arquivos pareados agora
 * @param kept arquivos que já tinham par gravado, com temporada e episódio mantidos como estão
 * @param episodesUpdated arquivos que já tinham par e tiveram os dados do episódio regravados
 *                        com os atuais do TMDB
 * @param unpaired arquivos que ficaram sem par, com o motivo
 */
public record TmdbFilePairingFill(
        Integer paired,
        Integer kept,
        Integer episodesUpdated,
        List<UnpairedVideoFile> unpaired
) {
}
