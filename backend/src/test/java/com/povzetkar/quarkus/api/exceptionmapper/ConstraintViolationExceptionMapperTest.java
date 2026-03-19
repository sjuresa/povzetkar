package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ConstraintViolationExceptionMapperTest {

    @Test
    void testToResponse() {
        ConstraintViolationExceptionMapper mapper = new ConstraintViolationExceptionMapper();

        ConstraintViolation<?> violation = mock(ConstraintViolation.class);
        Path path = mock(Path.class);
        when(path.toString()).thenReturn("testMethod.arg0.name");
        when(violation.getPropertyPath()).thenReturn(path);
        when(violation.getMessage()).thenReturn("must not be blank");

        ConstraintViolationException exception = new ConstraintViolationException("Validation failed", Set.of(violation));

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);
        
        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        assertThat(envelope.getData()).isNull();
        
        ApiErrorDTO error = envelope.getError();
        assertThat(error.type()).isEqualTo("/errors/validation");
        assertThat(error.title()).isEqualTo("Validation Error");
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.detail()).isEqualTo("One or more constraints have been violated.");
        assertThat(error.errorCode()).isEqualTo("VALIDATION_ERROR");
        
        assertThat(error.fieldErrors()).hasSize(1);
        assertThat(error.fieldErrors().getFirst().field()).isEqualTo("name");
        assertThat(error.fieldErrors().getFirst().message()).isEqualTo("must not be blank");
    }
}
