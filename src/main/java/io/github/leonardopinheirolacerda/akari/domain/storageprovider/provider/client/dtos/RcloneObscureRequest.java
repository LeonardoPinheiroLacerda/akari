package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Request do endpoint {@code /core/obscure} do rclone.
 *
 * @param clear valor em claro a ser obscurecido
 */
public record RcloneObscureRequest(
        String clear
) {
}
