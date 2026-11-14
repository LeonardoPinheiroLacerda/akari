package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Sistema externo recusou a chamada por excesso de requisições (HTTP {@code 429} do upstream) —
 * vira {@code 429} com {@code RATE_LIMITED}.
 *
 * <p>Diferente de {@link IntegrationException}: o sistema externo está no ar e respondeu, só não
 * aceita mais requisições por um tempo. A mensagem deve dizer ao consumidor da API quando tentar
 * de novo.
 */
public class RateLimitedException extends AkariException {

    /**
     * Cria a exceção para uma recusa por excesso de requisições.
     *
     * @param message qual sistema recusou e quando tentar de novo, legível para o consumidor da
     *                API
     */
    public RateLimitedException(String message) {
        super(429, ApiErrorCode.RATE_LIMITED, message, null);
    }
}
