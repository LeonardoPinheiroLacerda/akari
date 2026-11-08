package io.github.leonardopinheirolacerda.akari.domain.ingestion.model.enums;

/**
 * Situação de uma {@code Ingestion} como um todo — o agregado das tasks de todas as pastas do
 * subtree.
 *
 * <p>Hoje só {@link #PENDING} é gravado, na criação; nenhum fluxo avança o status ainda.
 */
public enum IngestionStatus {

    /** Criada, com as tasks sendo registradas, sem nenhuma etapa iniciada. */
    PENDING,

    /** Pelo menos uma task em processamento. */
    RUNNING,

    /** Todas as tasks terminaram sem falha. */
    COMPLETED,

    /** Todas as tasks terminaram, mas alguma falhou. */
    COMPLETED_WITH_ERRORS,

    /** A ingestão em si falhou, antes de as tasks terminarem. */
    FAILED,

    /** Interrompida antes de terminar. */
    CANCELLED
}
