package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * O recurso que se quer criar já existe, ou o estado atual impede a operação — vira {@code 409}
 * com {@code CONFLICT}.
 *
 * <p>Use quando a operação é válida na forma e o alvo existe, mas o pedido colide com o que já
 * está gravado (ex.: criar uma franquia manual com um anime que já é raiz). Para recurso
 * inexistente use {@link ResourceNotFoundException}; para outras regras violadas,
 * {@link BusinessRuleException}.
 */
public class ConflictException extends AkariException {

    /**
     * Cria a exceção para um conflito com o estado atual.
     *
     * @param message o que já existe ou o que impede a operação, específico o bastante para o
     *                consumidor da API entender o problema sem contexto adicional
     */
    public ConflictException(String message) {
        super(409, ApiErrorCode.CONFLICT, message, null);
    }
}
