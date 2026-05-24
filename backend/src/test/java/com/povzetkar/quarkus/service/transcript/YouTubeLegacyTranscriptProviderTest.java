package com.povzetkar.quarkus.service.transcript;

import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptContent.Fragment;
import io.github.thoroldvix.api.TranscriptList;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YouTubeLegacyTranscriptProviderTest {

    @Mock
    private YoutubeTranscriptApi youtubeTranscriptApi;

    @Mock
    private TranscriptList transcriptList;

    @Mock
    private io.github.thoroldvix.api.Transcript transcript;

    @Mock
    private TranscriptContent transcriptContent;

    private YouTubeLegacyTranscriptProvider provider;

    @BeforeEach
    void setUp() {
        provider = new YouTubeLegacyTranscriptProvider(youtubeTranscriptApi, true, 0, 0, new TimeTextFormatter());
    }

    @Test
    @DisplayName("should return formatted transcript on success")
    void shouldReturnFormattedTranscript() throws Exception {
        Fragment fragment = mock(Fragment.class);
        when(fragment.getStart()).thenReturn(0.0);
        when(fragment.getText()).thenReturn("Hello world.");

        when(youtubeTranscriptApi.listTranscripts("videoId")).thenReturn(transcriptList);
        when(transcriptList.findTranscript()).thenReturn(transcript);
        when(transcript.fetch()).thenReturn(transcriptContent);
        when(transcriptContent.getContent()).thenReturn(List.of(fragment));

        String result = provider.fetch("videoId");

        assertThat(result).contains("Hello world.");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on TranscriptRetrievalException")
    void shouldThrowProviderUnavailableOnRetrievalException() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("videoId"))
                .thenThrow(new TranscriptRetrievalException("videoId", "unavailable"));

        assertThatThrownBy(() -> provider.fetch("videoId"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("should throw ProviderUnavailableException on IllegalArgumentException")
    void shouldThrowProviderUnavailableOnInvalidFormat() throws Exception {
        when(youtubeTranscriptApi.listTranscripts("bad-id"))
                .thenThrow(new IllegalArgumentException("invalid video id"));

        assertThatThrownBy(() -> provider.fetch("bad-id"))
                .isInstanceOf(ProviderUnavailableException.class)
                .hasMessageContaining("youtube-legacy");
    }

    @Test
    @DisplayName("should report correct name and enabled state")
    void shouldReportNameAndEnabled() {
        assertThat(provider.name()).isEqualTo("youtube-legacy");
        assertThat(provider.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("should not apply jitter when min and max are zero")
    void shouldNotApplyJitterWhenZero() throws Exception {
        Fragment fragment = mock(Fragment.class);
        when(fragment.getStart()).thenReturn(0.0);
        when(fragment.getText()).thenReturn("text");
        when(youtubeTranscriptApi.listTranscripts("videoId")).thenReturn(transcriptList);
        when(transcriptList.findTranscript()).thenReturn(transcript);
        when(transcript.fetch()).thenReturn(transcriptContent);
        when(transcriptContent.getContent()).thenReturn(List.of(fragment));

        long start = System.currentTimeMillis();
        provider.fetch("videoId");
        long elapsed = System.currentTimeMillis() - start;

        assertThat(elapsed).isLessThan(500);
    }
}
