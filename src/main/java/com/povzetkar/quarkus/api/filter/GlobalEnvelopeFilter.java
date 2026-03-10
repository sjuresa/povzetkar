package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.List;

@Provider
@Enveloped
public class GlobalEnvelopeFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Only wrap JSON responses
        MediaType mediaType = responseContext.getMediaType();
        if (mediaType == null || !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return;
        }

        Object entity = responseContext.getEntity();
        if (entity == null || entity instanceof ApiEnvelopeDTO) {
            return;
        }

        // Handle PaginatedResponse wrapping
        if (entity instanceof PaginatedResponse<?>(
                List<?> items, long totalCount, int pageIndex, int pageSize
        )) {
            responseContext.setEntity(ApiEnvelopeDTO.paginated(items, totalCount, pageIndex, pageSize));
        } else {
            // Standard success wrap for raw DTOs or Lists
            responseContext.setEntity(ApiEnvelopeDTO.success(entity));
        }
    }
}
