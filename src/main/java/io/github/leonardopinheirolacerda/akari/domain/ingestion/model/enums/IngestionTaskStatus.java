package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Situação de uma {@code IngestionTask} — o processamento de uma pasta dentro de uma ingestão.
 *
 * <p>{@link #CANCELLED} ainda não é gravado por nenhum fluxo.
 */
public enum IngestionTaskStatus {

    /** Pasta e arquivos registrados no catalog, nenhuma etapa iniciada. */
    PENDING,

    /** Tomada pelo scheduler, com os steps em execução. */
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
