package com.povzetkar.quarkus.api.dto;

import java.util.List;

public class BriefingPaginatedResponse extends PaginatedResponse<BriefingSummaryDTO> {
    public BriefingPaginatedResponse() {
    }

    public BriefingPaginatedResponse(List<BriefingSummaryDTO> items, long totalCount, int pageIndex, int pageSize) {
        super(items, totalCount, pageIndex, pageSize);
    }
}
