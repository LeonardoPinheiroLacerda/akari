package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Request do endpoint {@code /job/stop} do rclone.
 *
 * @param jobid identificador do job a interromper
 */
public record RcloneJobStopRequest(
        Long jobid
) {
}
