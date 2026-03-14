package com.povzetkar.quarkus.api.dto;

import java.util.List;

public record PaginatedResponse<T>(
        List<T> items,
        long totalCount,
        int pageIndex,
        int pageSize) {
}
