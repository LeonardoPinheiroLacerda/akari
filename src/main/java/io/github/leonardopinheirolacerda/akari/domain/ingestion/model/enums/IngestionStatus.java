package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Situação de uma {@code Ingestion} como um todo — o agregado das tasks de todas as pastas do
 * subtree.
 *
 * <p>Gravados: {@link #PENDING} na criação, {@link #RUNNING} quando a primeira task começa, e
 * {@link #COMPLETED} ou {@link #COMPLETED_WITH_ERRORS} quando a última termina (ver
 * {@code IngestionTaskExecutionService}). {@link #FAILED} e {@link #CANCELLED} não são gravados
 * por nenhum fluxo.
 */
public enum IngestionStatus {

    /** Criada, com as tasks sendo registradas, sem nenhuma etapa iniciada. */
    PENDING,

    /** Pelo menos uma task em processamento. */
    RUNNING,

    /** Todas as tasks terminaram sem falha — inclusive as que pararam à espera de curadoria. */
    COMPLETED,

    /** Todas as tasks terminaram, mas alguma falhou. */
    COMPLETED_WITH_ERRORS,

    /** A ingestão em si falhou, antes de as tasks terminarem. */
    FAILED,

    /** Interrompida antes de terminar. */
    CANCELLED
}
