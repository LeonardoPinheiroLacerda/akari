package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Opções aceitas pelo endpoint {@code /operations/list} do rclone. Só usamos {@code recurse}
 * — as demais opções ficam com o default do rclone.
 *
 * @param recurse se {@code true}, lista toda a subárvore a partir do path
 */
public record RcloneListOptionsRequest(
        boolean recurse
) {
}
