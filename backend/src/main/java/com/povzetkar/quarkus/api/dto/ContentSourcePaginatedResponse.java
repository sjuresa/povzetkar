package com.povzetkar.quarkus.api.dto;

import java.util.List;

public class ContentSourcePaginatedResponse extends PaginatedResponse<ContentSourceDTO> {
    public ContentSourcePaginatedResponse() {
    }

    public ContentSourcePaginatedResponse(List<ContentSourceDTO> items, long totalCount, int pageIndex, int pageSize) {
        super(items, totalCount, pageIndex, pageSize);
    }
}
