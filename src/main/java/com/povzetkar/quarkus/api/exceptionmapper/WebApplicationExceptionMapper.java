package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class WebApplicationExceptionMapper implements ExceptionMapper<WebApplicationException> {

    @Override
    public Response toResponse(WebApplicationException exception) {
        Response response = exception.getResponse();
        int status = response.getStatus();
        Response.StatusType statusInfo = response.getStatusInfo();
        
        String title = statusInfo.getReasonPhrase();
        String type = "/errors/" + title.toLowerCase().replaceAll("\\W+", "-");
        String errorCode = title.toUpperCase().replaceAll("\\W+", "_");
        
        String detail = exception.getMessage();
        if (status >= 500 || detail == null || detail.isBlank() || detail.startsWith("HTTP ")) {
            detail = title;
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
