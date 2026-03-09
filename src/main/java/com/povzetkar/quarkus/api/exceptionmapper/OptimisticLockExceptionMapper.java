package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.api.dto.ApiMetaDTO;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;

@Provider
public class OptimisticLockExceptionMapper implements ExceptionMapper<OptimisticLockException> {

    @Override
    public Response toResponse(OptimisticLockException exception) {
        ApiErrorDTO error = new ApiErrorDTO(
                "/errors/conflict",
                "Conflict",
                Response.Status.CONFLICT.getStatusCode(),
                exception.getMessage() != null ? exception.getMessage() : "The entity was modified or deleted by another transaction.",
                "OPTIMISTIC_LOCK_ERROR",
                null
        );

        ApiEnvelopeDTO<Void> envelope = new ApiEnvelopeDTO<>(
                null,
                new ApiMetaDTO(Instant.now(), null, null),
                error
        );

        return Response.status(Response.Status.CONFLICT)
                .entity(envelope)
                .build();
    }
}
