package com.povzetkar.quarkus.api.exceptionmapper;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import io.quarkus.security.ForbiddenException;
import io.quarkus.security.UnauthorizedException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityExceptionMapperTest {

    @Test
    void testToResponse_Unauthorized() {
        SecurityExceptionMapper mapper = new SecurityExceptionMapper();
        UnauthorizedException exception = new UnauthorizedException("Not logged in");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(401);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(401);
        assertThat(error.title()).isEqualTo("Unauthorized");
        assertThat(error.type()).isEqualTo("/errors/unauthorized");
        assertThat(error.errorCode()).isEqualTo("UNAUTHORIZED");
        assertThat(error.detail()).isEqualTo("Authentication is required and has failed or has not yet been provided.");
    }

    @Test
    void testToResponse_Forbidden() {
        SecurityExceptionMapper mapper = new SecurityExceptionMapper();
        ForbiddenException exception = new ForbiddenException("No access");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(403);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(403);
        assertThat(error.title()).isEqualTo("Forbidden");
        assertThat(error.type()).isEqualTo("/errors/forbidden");
        assertThat(error.errorCode()).isEqualTo("FORBIDDEN");
        assertThat(error.detail()).isEqualTo("The server understood the request but refuses to authorize it.");
    }

    @Test
    void testToResponse_GenericSecurityException() {
        SecurityExceptionMapper mapper = new SecurityExceptionMapper();
        SecurityException exception = new SecurityException("General security failure");

        Response response = mapper.toResponse(exception);

        assertThat(response.getStatus()).isEqualTo(403);

        ApiEnvelopeDTO<?> envelope = (ApiEnvelopeDTO<?>) response.getEntity();
        ApiErrorDTO error = envelope.getError();
        assertThat(error.status()).isEqualTo(403);
        assertThat(error.title()).isEqualTo("Forbidden");
    }
}
