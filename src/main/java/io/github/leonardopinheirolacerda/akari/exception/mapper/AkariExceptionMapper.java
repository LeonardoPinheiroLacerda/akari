package io.github.leonardopinheirolacerda.akari.exception.mapper;

import io.github.leonardopinheirolacerda.akari.exception.AkariException;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduz as exceções da aplicação ({@link AkariException} e subclasses) no {@code ApiError} do
 * contrato.
 *
 * <p>Não decide status nem código: cada exceção já carrega os seus. Descoberto pelo Quarkus REST
 * via {@link ServerExceptionMapper} — sem registro manual.
 */
public class AkariExceptionMapper {

    /**
     * Responde com o status, o código e a mensagem que a exceção carrega, sem {@code details}.
     *
     * @param e exceção lançada pela aplicação
     * @return resposta com o status de {@link AkariException#status()} e o {@code ApiError}
     *         correspondente
     */
    @ServerExceptionMapper
    public Response map(AkariException e) {
        return ApiErrorResponses.of(e.status(), e.code(), e.getMessage(), e);
    }
}
