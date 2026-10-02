package io.github.leonardopinheirolacerda.akari.clients.rclone.dtos;

/**
 * Request do endpoint {@code /operations/list} do rclone.
 *
 * @param fs filesystem descriptor (ex.: {@code ":mega,user=x,pass=y:"})
 * @param remote path relativo dentro do filesystem (string vazia = raiz)
 * @param opt opções de listagem (recursão, etc.)
 */
public record RcloneListRequest(
        String fs,
        String remote,
        RcloneListOptionsRequest opt
) {
}
