package io.github.leonardopinheirolacerda.akari.exceptions.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Rede de segurança: traduz qualquer exceção sem mapper mais específico em {@code 500} com
 * {@code INTERNAL_ERROR}.
 *
 * <p>O Quarkus escolhe o mapper pelo tipo mais próximo da exceção, então este só atua quando
 * nenhum outro casa. Sem ele, o erro sairia com o corpo default do Quarkus, fora do contrato.
 */
public class UnexpectedExceptionMapper {

    /**
     * Responde {@code 500} com a mensagem fixa {@code "Erro inesperado no servidor"} — a da
     * exceção pode vazar detalhes internos (SQL, caminhos de arquivo); o detalhe completo vai para
     * o log em {@code ERROR}.
     *
     * @param e exceção que escapou de um resource sem tradução específica
     * @return resposta {@code 500} com o {@code ApiError}
     */
    @ServerExceptionMapper
    public Response map(Throwable e) {
        return ApiErrorResponses.of(
                500,
                ApiErrorCode.INTERNAL_ERROR,
                "Erro inesperado no servidor",
                e
        );
    }
}
