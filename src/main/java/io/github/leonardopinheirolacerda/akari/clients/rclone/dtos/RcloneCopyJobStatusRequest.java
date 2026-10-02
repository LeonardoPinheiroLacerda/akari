package io.github.leonardopinheirolacerda.akari.clients.rclone.dtos;

/**
 * Request do endpoint {@code /job/status} do rclone.
 *
 * @param jobid identificador do job que se deseja consultar
 */
public record RcloneCopyJobStatusRequest(
        Long jobid
) {
}
