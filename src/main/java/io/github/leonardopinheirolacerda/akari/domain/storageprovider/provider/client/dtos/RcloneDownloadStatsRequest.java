package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Request do endpoint {@code /core/stats} do rclone.
 *
 * @param group identificador do grupo cujas estatísticas serão consultadas. Pra
 *              acompanhar um download individual, use o formato {@code "job/<jobid>"}
 */
public record RcloneDownloadStatsRequest(
        String group
) {
}
