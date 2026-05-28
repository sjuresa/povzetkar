package com.povzetkar.quarkus.service.transcript;

import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
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
        verify(proxyClient).getTranscript(eq("videoId"));
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
    @DisplayName("should throw ProviderUnavailableException on 404 video_unavailable")
    void shouldThrowProviderUnavailableOn404VideoUnavailable() {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(404);
        lenient().when(response.readEntity(String.class)).thenReturn("{\"detail\":{\"reason\":\"video_unavailable\"}}");

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 404 not_available_yet")
    void shouldThrowProviderUnavailableOn404NotAvailableYet() {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(404);
        lenient().when(response.readEntity(String.class)).thenReturn("{\"detail\":{\"reason\":\"not_available_yet\"}}");

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 502 proxy_blocked")
    void shouldThrowProviderUnavailableOn502ProxyBlocked() {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(502);
        lenient().when(response.readEntity(String.class)).thenReturn("{\"detail\":{\"reason\":\"proxy_blocked\"}}");

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 502 upstream_error")
    void shouldThrowProviderUnavailableOn502UpstreamError() {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(502);
        lenient().when(response.readEntity(String.class)).thenReturn("{\"detail\":{\"reason\":\"upstream_error\"}}");

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("proxy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 401")
    void shouldThrowProviderUnavailableOn401() {
        when(proxyClient.getTranscript(anyString())).thenReturn(response);
        when(response.getStatus()).thenReturn(401);
        lenient().when(response.readEntity(String.class)).thenReturn("{\"detail\":\"Invalid or missing API key.\"}");

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
}
