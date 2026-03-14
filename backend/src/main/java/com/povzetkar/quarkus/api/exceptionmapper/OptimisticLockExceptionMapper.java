package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.persistence.OptimisticLockException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

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

        ApiEnvelopeDTO<Void> envelope = ApiEnvelopeDTO.error(error);

        return Response.status(Response.Status.CONFLICT)
                .entity(envelope)
                .build();
    }
}
