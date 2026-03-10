package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BadRequestExceptionMapper implements ExceptionMapper<BadRequestException> {

    @Override
    public Response toResponse(BadRequestException exception) {
        ApiErrorDTO error = new ApiErrorDTO(
                "/errors/bad-request",
                "Bad Request",
                Response.Status.BAD_REQUEST.getStatusCode(),
                exception.getMessage(),
                "BAD_REQUEST",
                null
        );
 
        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
