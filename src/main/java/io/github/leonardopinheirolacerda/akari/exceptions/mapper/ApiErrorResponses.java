package io.github.leonardopinheirolacerda.akari.exceptions.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiError;
import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;
import io.github.leonardopinheirolacerda.akari.api.dto.ErrorDetail;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Monta a resposta de erro com o {@link ApiError} do contrato e loga a exceção — usado por todos
 * os mappers do pacote.
 *
 * <p>Estático e sem estado: só concentra a construção da resposta e a política de log, para os
 * mappers não repetirem as duas coisas. Erros {@code 5xx} são logados em {@code ERROR} com stack
 * trace; os demais em {@code WARN}, só com a mensagem.
 */
final class ApiErrorResponses {

    private static final Logger LOG = Logger.getLogger(ApiErrorResponses.class);

    private ApiErrorResponses() {
    }

    /**
     * Monta a resposta de erro sem violações por campo ({@code details} vazio) e loga a exceção.
     *
     * @param status status HTTP da resposta
     * @param code chave estável do erro
     * @param message mensagem devolvida ao cliente; pode diferir da mensagem da exceção para não
     *                vazar detalhes internos
     * @param e exceção que originou o erro, usada só no log
     * @return resposta com o status informado e o {@code ApiError} no corpo
     */
    static Response of(Integer status, ApiErrorCode code, String message, Throwable e) {
        return of(status, code, message, List.of(), e);
    }

    /**
     * Monta a resposta de erro com as violações por campo e loga a exceção.
     *
     * @param status status HTTP da resposta
     * @param code chave estável do erro
     * @param message mensagem devolvida ao cliente; pode diferir da mensagem da exceção para não
     *                vazar detalhes internos
     * @param details violações por campo; lista vazia quando o erro não é de validação
     * @param e exceção que originou o erro, usada só no log
     * @return resposta com o status informado e o {@code ApiError} no corpo
     */
    static Response of(
            Integer status,
            ApiErrorCode code,
            String message,
            List<ErrorDetail> details,
            Throwable e) {
        if (status >= 500) {
            LOG.error(e.getMessage(), e);
        } else {
            LOG.warn(e.getMessage());
        }

        return Response.status(status)
                .entity(new ApiError()
                        .status(status)
                        .code(code)
                        .message(message)
                        .details(details))
                .build();
    }
}
