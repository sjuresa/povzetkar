package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionMapperTest {

    @Test
    void testToResponse_GenericException() {
        GlobalExceptionMapper mapper = new GlobalExceptionMapper();
        Exception exception = new RuntimeException("Some unhandled database error");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(500);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(500);
        assertThat(error.title()).isEqualTo("Internal Server Error");
        assertThat(error.type()).isEqualTo("/errors/internal-server-error");
        assertThat(error.errorCode()).isEqualTo("INTERNAL_SERVER_ERROR");
        assertThat(error.detail()).isEqualTo("An unexpected error occurred.");
    }
}
