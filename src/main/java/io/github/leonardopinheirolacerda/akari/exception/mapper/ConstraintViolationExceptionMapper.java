package io.github.leonardopinheirolacerda.akari.exception.mapper;

import io.github.leonardopinheirolacerda.akari.api.dto.ApiErrorCode;
import io.github.leonardopinheirolacerda.akari.api.dto.ErrorDetail;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ElementKind;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

import java.util.List;

/**
 * Traduz falhas de Bean Validation em {@code 400} com {@code VALIDATION_ERROR}, listando cada
 * violação em {@code details}.
 *
 * <p>Cobre as anotações que o gerador põe nos DTOs e nos parâmetros das interfaces geradas
 * ({@code @NotNull}, {@code @Min}, {@code minLength} do contrato, etc.). Substitui o mapper
 * default do Quarkus, que responde num shape fora do contrato.
 */
public class ConstraintViolationExceptionMapper {

    /**
     * Responde {@code 400} com uma entrada em {@code details} por violação: o campo violado e a
     * mensagem do validador.
     *
     * @param e exceção com as violações encontradas na requisição
     * @return resposta {@code 400} com o {@code ApiError} e as violações em {@code details}
     */
    @ServerExceptionMapper
    public Response map(ConstraintViolationException e) {
        final List<ErrorDetail> details = e.getConstraintViolations()
                .stream()
                .map(v -> new ErrorDetail()
                        .field(fieldOf(v.getPropertyPath()))
                        .error(v.getMessage()))
                .toList();

        return ApiErrorResponses.of(400, ApiErrorCode.VALIDATION_ERROR, "Payload ou parâmetros inválidos", details, e);
    }

    // O caminho vem como "método.argumento.campo...". Campo do body -> só os campos
    // ("config.password", "items[2].name"); parâmetro de query/path -> o nome dele ("size").
    private static String fieldOf(Path path) {
        final StringBuilder field = new StringBuilder();
        String parameter = "";

        for (Path.Node node : path) {
            if (node.getKind() == ElementKind.PARAMETER) {
                parameter = node.getName();
                continue;
            }
            if (node.getKind() != ElementKind.PROPERTY && node.getKind() != ElementKind.CONTAINER_ELEMENT) {
                continue;
            }
            // O índice de "items[2].name" vem no nó seguinte ao da coleção (o de "name").
            if (node.isInIterable() && !field.isEmpty()) {
                final Object position = node.getIndex() != null ? node.getIndex() : node.getKey();
                field.append('[').append(position == null ? "" : position).append(']');
            }
            if (node.getKind() == ElementKind.PROPERTY) {
                if (!field.isEmpty()) {
                    field.append('.');
                }
                field.append(node.getName());
            }
        }

        return field.isEmpty() ? parameter : field.toString();
    }
}
