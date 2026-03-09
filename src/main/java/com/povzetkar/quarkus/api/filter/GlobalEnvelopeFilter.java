package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class GlobalEnvelopeFilter implements ContainerResponseFilter {

    private static final String UI_PATH_PREFIX = "ui/";
    private static final String Q_PATH_PREFIX = "q/";

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();

        // Skip non-API paths (UI, Swagger UI, OpenAPI spec, etc.)
        if (path.startsWith(UI_PATH_PREFIX) || path.startsWith(Q_PATH_PREFIX)) {
            return;
        }

        // Only wrap JSON responses
        if (responseContext.getMediaType() != null && 
            !responseContext.getMediaType().isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return;
        }

        Object entity = responseContext.getEntity();
        if (entity == null) {
            return;
        }

        // If the entity is already an envelope (e.g. from an exception mapper or legacy manual wrap), skip it
        if (entity instanceof ApiEnvelopeDTO) {
            return;
        }

        // Handle PaginatedResponse wrapping
        if (entity instanceof PaginatedResponse<?> (var items, var totalCount)) {
            String pageStr = requestContext.getUriInfo().getQueryParameters().getFirst("page");
            String sizeStr = requestContext.getUriInfo().getQueryParameters().getFirst("size");
            
            int pageIndex = pageStr != null ? Integer.parseInt(pageStr) : 0;
            int pageSize = sizeStr != null ? Integer.parseInt(sizeStr) : 20;
            
            responseContext.setEntity(ApiEnvelopeDTO.paginated(items, totalCount, pageIndex, pageSize));
        } else {
            // Standard success wrap for raw DTOs or Lists
            responseContext.setEntity(ApiEnvelopeDTO.success(entity));
        }
    }
}
