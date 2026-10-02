package io.github.leonardopinheirolacerda.akari.clients.rclone.dtos;

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
