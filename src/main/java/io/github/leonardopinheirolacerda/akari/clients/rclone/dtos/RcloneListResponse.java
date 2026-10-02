package io.github.leonardopinheirolacerda.akari.clients.rclone.dtos;

import java.util.List;

/**
 * Response do endpoint {@code /operations/list}: envelope com a lista de itens encontrados.
 *
 * @param list itens no path solicitado (arquivos e diretórios). Nunca é {@code null} — se
 *             não houver conteúdo, vem lista vazia
 */
public record RcloneListResponse(
        List<RcloneItemResponse> list
) {
}
