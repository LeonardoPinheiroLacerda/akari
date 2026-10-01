package io.github.leonardopinheirolacerda.akari.exception.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduz os erros do próprio runtime JAX-RS ({@link WebApplicationException}) no
 * {@code ApiError} do contrato, preservando o status.
 *
 * <p>Cobre rota inexistente e parâmetro de path não conversível ({@code 404}), método não
 * suportado ({@code 405}), {@code Accept} ou {@code Content-Type} não suportados
 * ({@code 406}/{@code 415}), etc. Sem ele, esses casos sairiam com o corpo default do Quarkus.
 */
public class WebApplicationExceptionMapper {

    /**
     * Responde com o status da exceção e o código derivado dele: {@code 400} vira
     * {@code VALIDATION_ERROR}, {@code 404} vira {@code RESOURCE_NOT_FOUND}, os demais {@code 4xx}
     * viram {@code REQUEST_ERROR} e {@code 5xx} vira {@code INTERNAL_ERROR}.
     *
     * @param e erro lançado pelo runtime JAX-RS (ou por código que lançou um
     *          {@link WebApplicationException} diretamente)
     * @return resposta com o mesmo status da exceção e o {@code ApiError} correspondente
     */
    @ServerExceptionMapper
    public Response map(WebApplicationException e) {
        final int status = e.getResponse().getStatus();

        final ApiErrorCode code;

        if (status == 400) {
            code = ApiErrorCode.VALIDATION_ERROR;
        } else if (status == 404) {
            code = ApiErrorCode.RESOURCE_NOT_FOUND;
        } else if (status < 500) {
            code = ApiErrorCode.REQUEST_ERROR;
        } else {
            code = ApiErrorCode.INTERNAL_ERROR;
        }

        return ApiErrorResponses.of(status, code, e.getMessage(), e);
    }
}
