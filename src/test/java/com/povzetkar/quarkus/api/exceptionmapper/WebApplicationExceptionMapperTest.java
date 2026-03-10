package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WebApplicationExceptionMapperTest {

    @Test
    void testToResponse_BadRequest() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        BadRequestException exception = new BadRequestException("Invalid parameter");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(400);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        assertThat(envelope.getData()).isNull();

        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(400);
        assertThat(error.title()).isEqualTo("Bad Request");
        assertThat(error.detail()).isEqualTo("Invalid parameter");
        assertThat(error.errorCode()).isEqualTo("BAD_REQUEST");
        assertThat(error.type()).isEqualTo("/errors/bad-request");
    }

    @Test
    void testToResponse_NotFound() {
        WebApplicationExceptionMapper mapper = new WebApplicationExceptionMapper();
        NotFoundException exception = new NotFoundException("Resource not found");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(404);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(404);
        assertThat(error.title()).isEqualTo("Not Found");
        assertThat(error.detail()).isEqualTo("Resource not found");
        assertThat(error.errorCode()).isEqualTo("NOT_FOUND");
        assertThat(error.type()).isEqualTo("/errors/not-found");
    }
}
