package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/** Situação de uma tentativa de execução de uma {@link IngestionStep} numa task. */
public enum IngestionStepStatus {

    /** Registrada, ainda não iniciada. */
    PENDING,

    /** Em execução. */
    RUNNING,

    /** Terminou sem falha. */
    COMPLETED,

    /** Terminou com falha. */
    FAILED,

    /** Não executada, por não se aplicar à pasta. */
    SKIPPED
}
