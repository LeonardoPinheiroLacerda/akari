package io.github.leonardopinheirolacerda.akari.exception;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Preferência obrigatória sem valor — vira {@code 503} com {@code INTEGRATION_NOT_CONFIGURED}.
 *
 * <p>É uma precondição de setup, não uma falha de sistema: a funcionalidade fica indisponível até
 * o admin preencher a preferência em {@code PUT /v1/settings/{key}} (ex.: {@code tmdb.api-key},
 * {@code cache.dir}). O código estável permite ao portal convidar o admin a configurar.
 */
public class IntegrationNotConfiguredException extends AkariException {

    /**
     * Cria a exceção para uma preferência obrigatória sem valor.
     *
     * @param message qual preferência falta e como configurá-la (ex.: "Preferência 'tmdb.api-key'
     *                não está configurada — configure pelo portal")
     */
    public IntegrationNotConfiguredException(String message) {
        super(503, ApiErrorCode.INTEGRATION_NOT_CONFIGURED, message, null);
    }
}
