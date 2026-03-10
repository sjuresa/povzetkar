package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.api.dto.FieldErrorDTO;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.List;

@Provider
public class ConstraintViolationExceptionMapper implements ExceptionMapper<ConstraintViolationException> {

    @Override
    public Response toResponse(ConstraintViolationException exception) {
        List<FieldErrorDTO> fieldErrors = exception.getConstraintViolations().stream()
                .map(violation -> {
                    String path = violation.getPropertyPath().toString();
                    // Extract just the field name from the path if possible (e.g. from "method.arg0.fieldName" to "fieldName")
                    int lastDotIndex = path.lastIndexOf('.');
                    String fieldName = (lastDotIndex != -1) ? path.substring(lastDotIndex + 1) : path;
                    return new FieldErrorDTO(fieldName, violation.getMessage());
                })
                .toList();

        ApiErrorDTO error = new ApiErrorDTO(
                "/errors/validation",
                "Validation Error",
                Response.Status.BAD_REQUEST.getStatusCode(),
                "One or more constraints have been violated.",
                "VALIDATION_ERROR",
                fieldErrors.isEmpty() ? null : fieldErrors
        );

        return Response.status(Response.Status.BAD_REQUEST)
                .entity(ApiEnvelopeDTO.error(error))
                .build();
    }
}
