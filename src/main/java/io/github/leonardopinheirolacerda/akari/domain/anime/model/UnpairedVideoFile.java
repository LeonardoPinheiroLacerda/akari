package io.github.leonardopinheirolacerda.akari.domain.anime.model;

/**
 * Arquivo de vídeo que o preenchimento automático de pairing deixou sem par (ver
 * {@code AnimeTmdbFilePairingFillService#fillPairings}).
 *
 * @param fileId id do arquivo no catalog
 * @param fileName nome do arquivo
 * @param reason motivo: o status da resolução ({@code NO_EPISODE_NUMBER}, {@code NOT_MAPPED},
 *               {@code NOT_FOUND_IN_TMDB}, {@code OUT_OF_AIR_WINDOW}...) ou
 *               {@code EPISODE_CONFLICT}, quando o episódio resolvido também é de outro arquivo
 *               da pasta
 * @param seasonNumber temporada resolvida; {@code null} quando a resolução não chegou a um
 *                     episódio
 * @param episodeNumber episódio resolvido; {@code null} quando a resolução não chegou a um
 *                      episódio
 */
public record UnpairedVideoFile(
        Integer fileId,
        String fileName,
        String reason,
        Integer seasonNumber,
        Integer episodeNumber
) {
}
