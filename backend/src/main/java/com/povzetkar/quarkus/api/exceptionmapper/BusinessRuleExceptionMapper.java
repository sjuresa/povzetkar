package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class BusinessRuleExceptionMapper implements ExceptionMapper<BusinessRuleException> {

    @Override
    public Response toResponse(BusinessRuleException exception) {
        int status = exception.status.getStatusCode();
        String type = "/errors/" + exception.code.toLowerCase().replace('_', '-');

        ApiErrorDTO error = new ApiErrorDTO(
                type,
                exception.status.getReasonPhrase(),
                status,
                exception.getMessage(),
                exception.code,
                null
        );

        return Response.status(status)
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
