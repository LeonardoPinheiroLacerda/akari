package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Parâmetro de request mal formado ou fora do permitido — vira {@code 400} com
 * {@code VALIDATION_ERROR}.
 *
 * <p>Use pra entrada que não corresponde ao contrato em si (ex.: spec de sort com campo fora da
 * whitelist). Para input que respeita o contrato mas fere uma regra de negócio, use
 * {@link BusinessRuleException}.
 */
public class RequestValidationException extends AkariException {

    /**
     * Cria a exceção para um parâmetro de request inválido.
     *
     * @param message o que está inválido no parâmetro, específico o bastante para o consumidor da
     *                API corrigir a requisição
     */
    public RequestValidationException(String message) {
        super(400, ApiErrorCode.VALIDATION_ERROR, message, null);
    }
}