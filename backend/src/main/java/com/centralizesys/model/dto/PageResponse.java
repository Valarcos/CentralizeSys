package com.centralizesys.model.dto;

import java.util.List;

public record PageResponse<T>(
        List<T> content,
        Long page,
        Long size,
        Long totalElements,
        Long totalPages) {
}
