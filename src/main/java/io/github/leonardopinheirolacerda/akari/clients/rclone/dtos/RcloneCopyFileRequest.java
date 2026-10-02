package io.github.leonardopinheirolacerda.akari.clients.rclone.dtos;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Request do endpoint {@code /operations/copyfile} do rclone. Carrega origem, destino e o
 * flag de execução assíncrona.
 *
 * @param srcFs filesystem descriptor de origem (ex.: {@code ":mega,user=x,pass=y:"})
 * @param srcRemote path do arquivo dentro da origem
 * @param dstFs filesystem descriptor de destino (tipicamente local, ex.: {@code "/downloads/"})
 * @param dstRemote nome/path do arquivo dentro do destino
 * @param async se {@code true}, rclone retorna um {@code jobid} imediatamente; se {@code false},
 *              bloqueia até o fim da cópia. Serializado como {@code _async} pra atender o
 *              contrato do rcd
 */
public record RcloneCopyFileRequest(
        String srcFs,
        String srcRemote,
        String dstFs,
        String dstRemote,
        @JsonProperty("_async") boolean async
) {
}
