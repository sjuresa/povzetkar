package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

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

    public T getData() {
        return data;
    }

    public ApiMetaDTO getMeta() {
        return meta;
    }

    public ApiErrorDTO getError() {
        return error;
    }
}
