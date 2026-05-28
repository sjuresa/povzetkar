package com.povzetkar.quarkus.service.transcript;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProxyTranscriptProviderTest {

    @Mock
    private TranscriptProxyClient proxyClient;

    @Mock
    private Response response;

    private ProxyTranscriptProvider provider;

    @BeforeEach
    void setUp() {
        provider = new ProxyTranscriptProvider(proxyClient, true, 0, 0, new TimeTextFormatter());
    }

    @Test
    @DisplayName("should return formatted transcript on 200 response")
    void shouldReturnFormattedTranscriptOn200() throws Exception {
        ProxyTranscriptResponse body = new ProxyTranscriptResponse(
                "videoId", List.of(new ProxySegment(0.0, "Hello world.")));

        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(ProxyTranscriptResponse.class)).thenReturn(body);

        String result = provider.fetch("videoId");

        assertThat(result).contains("Hello world.");
        verify(proxyClient).getTranscript("videoId");
    }

    @Test
    @DisplayName("should map start seconds directly to timestamp")
    void shouldMapStartSecondsToTimestamp() throws Exception {
        ProxyTranscriptResponse body = new ProxyTranscriptResponse(
                "videoId", List.of(new ProxySegment(60.0, "Text at one minute.")));

        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(ProxyTranscriptResponse.class)).thenReturn(body);

        String result = provider.fetch("videoId");

        assertThat(result).contains("01:00");
    }

    @Test
    @DisplayName("should return empty string when 200 response has empty segments")
    void shouldReturnEmptyOnEmptySegments() throws Exception {
        ProxyTranscriptResponse body = new ProxyTranscriptResponse("videoId", List.of());

        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(ProxyTranscriptResponse.class)).thenReturn(body);

        String result = provider.fetch("videoId");

        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException when 200 response has null segments")
    void shouldThrowProviderUnavailableOnNullSegments() {
        ProxyTranscriptResponse body = new ProxyTranscriptResponse("videoId", null);

        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(ProxyTranscriptResponse.class)).thenReturn(body);

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @ParameterizedTest(name = "HTTP {0} body {1}")
    @CsvSource({
            "404, '{\"detail\":{\"reason\":\"video_unavailable\"}}'",
            "404, '{\"detail\":{\"reason\":\"not_available_yet\"}}'",
            "502, '{\"detail\":{\"reason\":\"proxy_blocked\"}}'",
            "502, '{\"detail\":{\"reason\":\"upstream_error\"}}'",
            "401, '{\"detail\":\"Invalid or missing API key.\"}'"
    })
    @DisplayName("should throw ProviderUnavailableException on non-200 responses")
    void shouldThrowProviderUnavailableOnNon200(int status, String body) {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(status);
        lenient().when(response.readEntity(String.class)).thenReturn(body);

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on transport failure")
    void shouldThrowProviderUnavailableOnTransportFailure() {
        when(proxyClient.getTranscript(anyString())).thenThrow(new WebApplicationException(500));

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should report correct name and enabled state")
    void shouldReportNameAndEnabled() {
        assertThat(provider.name()).isEqualTo("proxy");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should not apply jitter on the first (isolated) call")
    void shouldNotApplyJitterOnFirstCall() throws Exception {
        // Large jitter window; if jitter were applied the call would block for >= jitterMin.
        ProxyTranscriptProvider jitteredProvider =
                new ProxyTranscriptProvider(proxyClient, true, 30_000, 60_000, new TimeTextFormatter());
        ProxyTranscriptResponse body = new ProxyTranscriptResponse("v", List.of(new ProxySegment(0.0, "Hi.")));
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(ProxyTranscriptResponse.class)).thenReturn(body);

        long start = System.nanoTime();
        jitteredProvider.fetch("v");
        long elapsedMs = (System.nanoTime() - start) / 1_000_000L;

        assertThat(elapsedMs).isLessThan(30_000L);
    }
}
