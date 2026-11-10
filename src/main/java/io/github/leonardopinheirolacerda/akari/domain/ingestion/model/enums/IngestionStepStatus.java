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

    /**
     * Terminou sem erro, mas sem conseguir decidir sozinha — a task vai pra
     * {@code WAITING_CURATION}. Diferente de {@link #COMPLETED}, não conta como step executado
     * (o step roda de novo se a task for reprocessada), e diferente de {@link #FAILED}, não houve
     * erro.
     */
    UNRESOLVED,

    /** Terminou com falha. */
    FAILED,

    /** Não executada, por não se aplicar à pasta. */
    SKIPPED
}
