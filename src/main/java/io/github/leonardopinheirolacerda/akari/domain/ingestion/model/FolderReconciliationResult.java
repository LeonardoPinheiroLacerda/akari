package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

/**
 * Resultado de reconciliar uma pasta no Step 3 da ingestão.
 *
 * @param folderId id da pasta (criada ou já existente)
 * @param folderName nome da pasta — carregado aqui pra quem chama não precisar buscar a
 *                   {@code MediaFolder} de novo fora de transação (a thread virtual da
 *                   ingestão não tem transação ambiente)
 * @param created {@code true} se a pasta foi criada agora, {@code false} se já existia
 * @param hasDirectVideos {@code true} se a pasta tem ao menos um vídeo direto — sinaliza que
 *                        ela precisa passar pelo auto-bind do Step 4
 */
public record FolderReconciliationResult(Integer folderId, String folderName, boolean created, boolean hasDirectVideos) {
}
