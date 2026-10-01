package io.github.leonardopinheirolacerda.akari.exceptions;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;

/**
 * Base das exceções da aplicação: cada subclasse fixa o status HTTP e o {@link ApiErrorCode} que
 * o cliente recebe.
 *
 * <p>Quando uma subclasse escapa de um resource, o {@link AkariExceptionMapper} responde com o
 * {@code ApiError} do contrato usando {@link #status()}, {@link #code()} e a mensagem da
 * exceção — por isso a mensagem deve ser legível para o consumidor da API.
 */
public abstract class AkariException extends RuntimeException {

    private final Integer status;
    private final ApiErrorCode code;

    /**
     * Cria a exceção com o status e o código que a subclasse representa.
     *
     * @param status status HTTP da resposta de erro (ex.: {@code 404})
     * @param code chave estável do erro, devolvida em {@code ApiError.code}
     * @param message mensagem devolvida em {@code ApiError.message}; deve fazer sentido para o
     *                consumidor da API
     * @param cause causa original, preservada para o log; {@code null} quando não há
     */
    protected AkariException(Integer status, ApiErrorCode code, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
        this.code = code;
    }

    /**
     * @return status HTTP da resposta de erro gerada por esta exceção
     */
    public Integer status() {
        return status;
    }

    /**
     * @return chave estável do erro, devolvida em {@code ApiError.code}
     */
    public ApiErrorCode code() {
        return code;
    }
}
