package com.povzetkar.quarkus.api.dto;

import java.util.List;

public class PaginatedResponse<T> {
    public List<T> items;
    public long totalCount;
    public int pageIndex;
    public int pageSize;

    public PaginatedResponse() {
        // Required for JSON deserialization
    }

    public PaginatedResponse(List<T> items, long totalCount, int pageIndex, int pageSize) {
        this.items = items;
        this.totalCount = totalCount;
        this.pageIndex = pageIndex;
        this.pageSize = pageSize;
    }
}
