package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Recurso procurado não existe — vira {@code 404} com {@code RESOURCE_NOT_FOUND}.
 *
 * <p>Use em operações que exigem o recurso presente (ler, atualizar, remover). Não use para
 * outras condições: regra violada é {@link BusinessRuleException}, falha de sistema externo é
 * {@link IntegrationException}.
 */
public class ResourceNotFoundException extends AkariException {

    /**
     * Cria a exceção para um recurso inexistente.
     *
     * @param message tipo do recurso e identificador buscado (ex.: "MediaFolder not found with
     *                id: 42")
     */
    public ResourceNotFoundException(String message) {
        super(404, ApiErrorCode.RESOURCE_NOT_FOUND, message, null);
    }
}
