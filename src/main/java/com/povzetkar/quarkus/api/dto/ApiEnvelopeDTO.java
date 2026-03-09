package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Standard API response envelope")
public class ApiEnvelopeDTO<T> {
    @Schema(description = "The actual response data")
    public T data;
    
    @Schema(description = "Metadata about the response")
    public ApiMetaDTO meta;
    
    @Schema(description = "Error details, null if successful")
    public ApiErrorDTO error;

    public ApiEnvelopeDTO() {}

    public ApiEnvelopeDTO(T data, ApiMetaDTO meta, ApiErrorDTO error) {
        this.data = data;
        this.meta = meta;
        this.error = error;
    }

    public static <T> ApiEnvelopeDTO<T> success(T data, ApiMetaDTO meta) {
        return new ApiEnvelopeDTO<>(data, meta, null);
    }
}
