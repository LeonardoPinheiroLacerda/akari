package io.github.leonardopinheirolacerda.akari.domain.ingestion.model;

import io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums.IngestionTaskStatus;

/**
 * Quantidade de tasks de uma ingestão num status — linha da contagem agrupada por status
 * ({@link IngestionTask#countByStatus}).
 *
 * @param status status das tasks
 * @param count quantidade de tasks no status
 */
public record IngestionTaskStatusCount(
        IngestionTaskStatus status,
        Long count
) {
}
