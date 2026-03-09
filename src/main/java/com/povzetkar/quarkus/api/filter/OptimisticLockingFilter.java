package com.povzetkar.quarkus.api.filter;

import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;

@Provider
public class OptimisticLockingFilter implements ContainerRequestFilter {

    private static final String IF_MATCH_HEADER = "If-Match";
    private static final String UI_PATH_PREFIX = "/ui/";
    private static final String ERROR_MSG = "Missing 'If-Match' header for optimistic locking";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String method = requestContext.getMethod();
        String path = requestContext.getUriInfo().getPath();

        // Skip UI routes
        if (path.startsWith(UI_PATH_PREFIX)) {
            return;
        }

        if ("PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method)) {
            String ifMatch = requestContext.getHeaderString(IF_MATCH_HEADER);
            if (ifMatch == null || ifMatch.trim().isEmpty()) {
                var errorDto = new com.povzetkar.quarkus.api.dto.ApiErrorDTO(
                        "/errors/precondition-required",
                        "Precondition Required",
                        Response.Status.PRECONDITION_REQUIRED.getStatusCode(),
                        ERROR_MSG,
                        "PRECONDITION_REQUIRED",
                        null
                );
                var envelope = new com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO<>(
                        null,
                        new com.povzetkar.quarkus.api.dto.ApiMetaDTO(java.time.Instant.now(), null, null),
                        errorDto
                );
                requestContext.abortWith(
                        Response.status(Response.Status.PRECONDITION_REQUIRED)
                                .entity(envelope)
                                .type(jakarta.ws.rs.core.MediaType.APPLICATION_JSON)
                                .build()
                );
            }
        }
    }
}
