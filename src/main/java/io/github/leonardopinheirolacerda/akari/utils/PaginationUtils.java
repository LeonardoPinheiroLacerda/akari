package io.github.leonardopinheirolacerda.akari.utils;

import io.github.leonardopinheirolacerda.akari.exceptions.RequestValidationException;
import io.quarkus.panache.common.Sort;

import java.util.List;
import java.util.Map;

/**
 * Converte specs de ordenação vindas da API (`campo[:asc|desc]`) num {@link Sort} do Panache.
 *
 * <p>Reutilizável por qualquer entidade paginada: cada chamador passa sua própria whitelist de
 * campos ordenáveis, mapeando o nome exposto na API pra expressão HQL correspondente.
 */
public final class PaginationUtils {

    private PaginationUtils() {
    }

    /**
     * Monta o {@link Sort} a partir das specs informadas, validando cada campo contra a
     * whitelist do chamador.
     *
     * @param sort specs no formato `campo` ou `campo:asc|desc`; {@code null} ou vazio vira
     *             {@link Sort#empty()}
     * @param sortableFields whitelist que mapeia o campo exposto na API pra expressão HQL
     *                       (ex.: {@code "hasFiles" -> "size(videoFiles)"})
     * @return o {@link Sort} pronto pra passar pro Panache
     * @throws RequestValidationException se algum campo não estiver na whitelist ou a direção
     *                                     informada não for `asc`/`desc`
     */
    public static Sort toSort(List<String> sort, Map<String, String> sortableFields) {
        if (sort == null || sort.isEmpty()) {
            return Sort.empty();
        }

        Sort result = Sort.empty();

        for (String spec : sort) {
            final String[] parts = spec.split(":", 2);
            final String field = parts[0];
            final String column = sortableFields.get(field);

            if (column == null) {
                throw new RequestValidationException(
                        "Campo de ordenação inválido: '%s'. Valores aceitos: %s"
                                .formatted(field, sortableFields.keySet())
                );
            }

            result = result.and(column, toDirection(parts.length > 1 ? parts[1] : "asc"));
        }

        return result;
    }

    private static Sort.Direction toDirection(String direction) {
        if (direction.equalsIgnoreCase("asc")) {
            return Sort.Direction.Ascending;
        }
        if (direction.equalsIgnoreCase("desc")) {
            return Sort.Direction.Descending;
        }

        throw new RequestValidationException(
                "Direção de ordenação inválida: '%s'. Valores aceitos: asc, desc".formatted(direction)
        );
    }

}