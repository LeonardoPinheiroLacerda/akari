package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Situação de uma {@code IngestionTask} — o processamento de uma pasta dentro de uma ingestão.
 *
 * <p>Hoje só {@link #PENDING} é gravado, na criação da task; nenhum fluxo avança o status ainda.
 */
public enum IngestionTaskStatus {

    /** Pasta e arquivos registrados no catalog, nenhuma etapa iniciada. */
    PENDING,

    /** Alguma etapa da pasta em processamento. */
    RUNNING,

    /** Processamento parado à espera de curadoria manual. */
    WAITING_CURATION,

    /** Todas as etapas da pasta terminaram. */
    COMPLETED,

    /** Alguma etapa da pasta falhou. */
    FAILED,

    /** Interrompida antes de terminar. */
    CANCELLED
}
