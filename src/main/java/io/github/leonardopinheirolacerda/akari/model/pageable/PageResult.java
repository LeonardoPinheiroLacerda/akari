package io.github.leonardopinheirolacerda.akari.model.pageable;

import java.util.List;

public record PageResult<T>(List<T> data, Integer page, Integer size, Long totalElements, Integer totalPages) {
}