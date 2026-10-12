package io.github.leonardopinheirolacerda.akari.model;

import java.util.List;

/**
 * Página genérica de resultado de uma consulta Panache — usado por praticamente todo domínio
 * com listagem paginada, convertido pro envelope de página do contrato (ex. {@code VideoFilePage})
 * pelo {@code *Mapper} de cada domínio.
 *
 * @param data itens da página atual
 * @param page número da página pedida, 0-based
 * @param size tamanho máximo de itens por página
 * @param totalElements total absoluto de elementos que existem para a query, sem paginação
 * @param totalPages total de páginas, calculado a partir de {@code totalElements} e {@code size}
 * @param <T> tipo do item paginado
 */
public record PageResult<T>(List<T> data, Integer page, Integer size, Long totalElements, Integer totalPages) {
}