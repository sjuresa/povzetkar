package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.util.StructuredLogger;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final StructuredLogger log = StructuredLogger.getLogger(GlobalExceptionMapper.class);

    @Override
    public Response toResponse(Exception exception) {
        log.error(exception, "event", "unhandled_exception", "error", exception.getMessage());

        ApiErrorDTO error = new ApiErrorDTO(
                "/errors/internal-server-error",
                "Internal Server Error",
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "An unexpected error occurred.",
                "INTERNAL_SERVER_ERROR",
                null
        );

        return Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
