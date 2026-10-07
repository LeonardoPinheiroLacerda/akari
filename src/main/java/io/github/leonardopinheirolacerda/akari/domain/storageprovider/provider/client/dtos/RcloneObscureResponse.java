package io.github.leonardopinheirolacerda.akari.domain.storageprovider.provider.client.dtos;

/**
 * Response do endpoint {@code /core/obscure} do rclone.
 *
 * @param obscured valor obscurecido no formato aceito pelo rclone (reversível pelo próprio
 *                 rclone; não é hash criptográfico)
 */
public record RcloneObscureResponse(
        String obscured
) {
}
