package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class SecurityExceptionMapper implements ExceptionMapper<SecurityException> {

    @Override
    public Response toResponse(SecurityException exception) {
        ApiErrorDTO error = switch (exception) {
            case UnauthorizedException _ -> new ApiErrorDTO(
                    "/errors/unauthorized",
                    "Unauthorized",
                    Response.Status.UNAUTHORIZED.getStatusCode(),
                    "Authentication is required and has failed or has not yet been provided.",
                    "UNAUTHORIZED",
                    null
            );
            case ForbiddenException _ -> new ApiErrorDTO(
                    "/errors/forbidden",
                    "Forbidden",
                    Response.Status.FORBIDDEN.getStatusCode(),
                    "The server understood the request but refuses to authorize it.",
                    "FORBIDDEN",
                    null
            );
            default -> new ApiErrorDTO(
                    "/errors/forbidden",
                    "Forbidden",
                    Response.Status.FORBIDDEN.getStatusCode(),
                    "Access denied.",
                    "FORBIDDEN",
                    null
            );
        };

        return Response.status(error.status())
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
