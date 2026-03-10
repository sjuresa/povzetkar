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
        int status;
        String title;
        String type;
        String errorCode;
        String detail;

        if (exception instanceof UnauthorizedException) {
            status = Response.Status.UNAUTHORIZED.getStatusCode();
            title = "Unauthorized";
            type = "/errors/unauthorized";
            errorCode = "UNAUTHORIZED";
            detail = "Authentication is required and has failed or has not yet been provided.";
        } else if (exception instanceof ForbiddenException) {
            status = Response.Status.FORBIDDEN.getStatusCode();
            title = "Forbidden";
            type = "/errors/forbidden";
            errorCode = "FORBIDDEN";
            detail = "The server understood the request but refuses to authorize it.";
        } else {
            status = Response.Status.FORBIDDEN.getStatusCode();
            title = "Forbidden";
            type = "/errors/forbidden";
            errorCode = "FORBIDDEN";
            detail = "Access denied.";
        }

        ApiErrorDTO error = new ApiErrorDTO(
                type,
                title,
                status,
                detail,
                errorCode,
                null
        );

        return Response.status(status)
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
