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
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SupadataTranscriptProviderTest {

    @Mock
    private SupadataClient supadataClient;

    @Mock
    private Response response;

    private SupadataTranscriptProvider provider;

    @BeforeEach
    void setUp() {
        provider = new SupadataTranscriptProvider(supadataClient, true, "en", new TimeTextFormatter());
    }

    @Test
    @DisplayName("should return formatted transcript on 200 response")
    void shouldReturnFormattedTranscriptOn200() throws Exception {
        SupadataChunk chunk = new SupadataChunk("Hello world.", 0L, 2000L, "en");
        SupadataTranscriptResponse body = new SupadataTranscriptResponse(List.of(chunk), "en", List.of("en"));

        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(SupadataTranscriptResponse.class)).thenReturn(body);

        String result = provider.fetch("videoId");

        assertThat(result).contains("Hello world.");
        verify(supadataClient).getTranscript(eq("https://www.youtube.com/watch?v=videoId"), eq("en"), eq(false));
    }

    @Test
    @DisplayName("should throw QuotaExhaustedException on 402 response")
    void shouldThrowQuotaExhaustedOn402() {
        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(402);

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(QuotaExhaustedException.class)
                .hasMessageContaining("supadata");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 404 response")
    void shouldThrowProviderUnavailableOn404() {
        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(404);

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("supadata");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on 403 response")
    void shouldThrowProviderUnavailableOn403() {
        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(403);

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("supadata");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on WebApplicationException")
    void shouldThrowProviderUnavailableOnWebApplicationException() {
        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean()))
                .thenThrow(new WebApplicationException(500));

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("supadata");
    }

    @Test
    @DisplayName("should convert millisecond offsets to seconds correctly")
    void shouldConvertMillisecondsToSeconds() throws Exception {
        SupadataChunk chunk = new SupadataChunk("Text at 1 minute.", 60_000L, 5000L, "en");
        SupadataTranscriptResponse body = new SupadataTranscriptResponse(List.of(chunk), "en", List.of("en"));

        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(SupadataTranscriptResponse.class)).thenReturn(body);

        String result = provider.fetch("videoId");

        assertThat(result).contains("01:00");
    }

    @Test
    @DisplayName("should report correct name and enabled state")
    void shouldReportNameAndEnabled() {
        assertThat(provider.name()).isEqualTo("supadata");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should construct correct YouTube URL from videoId")
    void shouldConstructCorrectYouTubeUrl() throws Exception {
        when(supadataClient.getTranscript(anyString(), anyString(), anyBoolean())).thenReturn(response);
        when(response.getStatus()).thenReturn(200);
        when(response.readEntity(SupadataTranscriptResponse.class))
                .thenReturn(new SupadataTranscriptResponse(List.of(), "en", List.of()));

        provider.fetch("abc123");

        verify(supadataClient).getTranscript("https://www.youtube.com/watch?v=abc123", "en", false);
    }
}
