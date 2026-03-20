package com.povzetkar.quarkus.api.filter;

import com.fasterxml.jackson.databind.JsonNode;
import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
@Enveloped
public class GlobalEnvelopeFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        // Only wrap successful (2xx) responses. Errors should be handled by ExceptionMappers.
        if (responseContext.getStatus() < 200 || responseContext.getStatus() >= 300) {
            return;
        }

        // Only wrap JSON responses
        MediaType mediaType = responseContext.getMediaType();
        if (mediaType == null || !mediaType.isCompatible(MediaType.APPLICATION_JSON_TYPE)) {
            return;
        }

        Object entity = responseContext.getEntity();
        if (entity == null || entity instanceof ApiEnvelopeDTO || entity instanceof JsonNode) {
            return;
        }

        // Wrap all 2xx responses — both paginated lists and raw DTOs
        responseContext.setEntity(ApiEnvelopeDTO.success(entity));
    }
}
