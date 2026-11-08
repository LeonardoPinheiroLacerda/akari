package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/** Situação de uma tentativa de execução de uma {@link IngestionStep} numa task. */
public enum IngestionStepStatus {

    /**
     * Registrada, ainda não iniciada — não gravado hoje: as execuções já nascem
     * {@link #RUNNING}.
     */
    PENDING,

    /** Em execução. */
    RUNNING,

    /** Terminou sem falha. */
    COMPLETED,

    /** Terminou sem erro, mas sem conseguir decidir sozinha; a task para à espera de curadoria. */
    WAITING_CURATION,

    /** Terminou com falha. */
    FAILED,

    /** Não executada, por não se aplicar à pasta. */
    SKIPPED
}
