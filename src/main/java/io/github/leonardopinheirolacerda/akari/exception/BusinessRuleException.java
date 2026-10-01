package io.github.leonardopinheirolacerda.akari.exception;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Input válido na forma, mas que fere uma regra de negócio — vira {@code 400} com
 * {@code BUSINESS_RULE_VIOLATION}.
 *
 * <p>Use quando o payload passou pela Bean Validation mas a operação não pode ser feita no estado
 * atual (ex.: enfileirar no cache um arquivo que já está baixado). Para recurso inexistente use
 * {@link ResourceNotFoundException}; para falha de sistema externo, {@link IntegrationException}.
 */
public class BusinessRuleException extends AkariException {

    /**
     * Cria a exceção para uma regra de negócio violada.
     *
     * @param message regra violada, específica o bastante para o consumidor da API entender o
     *                problema sem contexto adicional
     */
    public BusinessRuleException(String message) {
        super(400, ApiErrorCode.BUSINESS_RULE_VIOLATION, message, null);
    }
}
