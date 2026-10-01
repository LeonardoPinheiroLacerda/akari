package io.github.leonardopinheirolacerda.akari.exceptions.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Traduz body que não desserializa (JSON malformado, tipo incompatível, valor de enum
 * desconhecido) em {@code 400} com {@code VALIDATION_ERROR}.
 *
 * <p>Para o contrato, body que o Jackson não consegue ler é payload inválido, como uma violação
 * de Bean Validation. A mensagem é fixa porque a do Jackson expõe nomes de classes Java.
 */
public class JsonExceptionMapper {

    /**
     * Responde {@code 400} com a mensagem fixa {@code "JSON inválido"}, sem {@code details}.
     *
     * @param e falha do Jackson ao ler o body da requisição
     * @return resposta {@code 400} com o {@code ApiError}
     */
    // MismatchedInputException listado à parte: o Quarkus tem um mapper builtin para ele.
    @ServerExceptionMapper({JsonProcessingException.class, MismatchedInputException.class})
    public Response map(JsonProcessingException e) {
        return ApiErrorResponses.of(400, ApiErrorCode.VALIDATION_ERROR, "JSON inválido", e);
    }
}
