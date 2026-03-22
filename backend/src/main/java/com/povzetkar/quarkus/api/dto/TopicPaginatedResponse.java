package com.povzetkar.quarkus.api.dto;

import java.util.List;

public class TopicPaginatedResponse extends PaginatedResponse<TopicDTO> {
    public TopicPaginatedResponse() {
    }

    public TopicPaginatedResponse(List<TopicDTO> items, long totalCount, int pageIndex, int pageSize) {
        super(items, totalCount, pageIndex, pageSize);
    }
}
