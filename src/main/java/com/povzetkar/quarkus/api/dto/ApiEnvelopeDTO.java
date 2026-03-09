package com.povzetkar.quarkus.api.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;
import java.util.List;

@Schema(description = "Standard API response envelope")
public class ApiEnvelopeDTO<T> {
    @Schema(description = "The actual response data")
    private T data;
    
    @Schema(description = "Metadata about the response")
    private ApiMetaDTO meta;
    
    @Schema(description = "Error details, null if successful")
    private ApiErrorDTO error;

    public ApiEnvelopeDTO() {}

    public ApiEnvelopeDTO(T data, ApiMetaDTO meta, ApiErrorDTO error) {
        this.data = data;
        this.meta = meta;
        this.error = error;
    }

    public static <T> ApiEnvelopeDTO<T> success(T data, ApiMetaDTO meta) {
        return new ApiEnvelopeDTO<>(data, meta, null);
    }

    public static <T> ApiEnvelopeDTO<T> success(T data) {
        return new ApiEnvelopeDTO<>(data, new ApiMetaDTO(Instant.now(), null, null), null);
    }

    public static <T> ApiEnvelopeDTO<List<T>> paginated(List<T> data, long totalCount, int page, int size) {
        int totalPages = (int) Math.ceil((double) totalCount / size);
        boolean hasNext = page < totalPages - 1;
        PaginationDTO pagination = new PaginationDTO(page, size, totalCount, totalPages, hasNext);
        ApiMetaDTO meta = new ApiMetaDTO(Instant.now(), null, pagination);
        return new ApiEnvelopeDTO<>(data, meta, null);
    }

    @JsonProperty("items")
    public T getItems() {
        return data;
    }

    public ApiMetaDTO getMeta() {
        return meta;
    }

    @JsonProperty("totalCount")
    public Long getTotalCount() {
        if (meta != null && meta.pagination() != null) {
            return meta.pagination().totalElements();
        }
        return null;
    }

    public ApiErrorDTO getError() {
        return error;
    }
}
