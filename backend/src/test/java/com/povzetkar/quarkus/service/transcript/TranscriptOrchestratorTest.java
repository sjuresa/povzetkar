package com.povzetkar.quarkus.service.transcript;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TranscriptOrchestratorTest {

    @Mock
    private TranscriptProvider primaryProvider;

    @Mock
    private TranscriptProvider secondaryProvider;

    @Mock
    private TranscriptProviderQuotaService quotaService;

    @Mock
    private Instance<TranscriptProvider> providerInstance;

    private MeterRegistry registry;
    private TranscriptOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        when(primaryProvider.name()).thenReturn("youtube-legacy");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(secondaryProvider.name()).thenReturn("supadata");
        when(secondaryProvider.isEnabled()).thenReturn(true);

        when(providerInstance.spliterator()).thenAnswer(inv -> List.<TranscriptProvider>of(primaryProvider, secondaryProvider).spliterator());

        orchestrator = new TranscriptOrchestrator(providerInstance, List.of("youtube-legacy", "supadata"), quotaService, registry);
    }

    @Test
    @DisplayName("should return transcript from first provider when it succeeds")
    void shouldReturnTranscriptFromFirstProvider() throws Exception {
        when(primaryProvider.fetch("videoId")).thenReturn("transcript text");

        String result = orchestrator.fetch("videoId");

        assertThat(result).isEqualTo("transcript text");
        verify(secondaryProvider, never()).fetch(anyString());
        verify(quotaService).recordFetch("youtube-legacy");
    }

    @Test
    @DisplayName("should fall through to secondary provider on QuotaExhaustedException")
    void shouldFallThroughOnQuotaExhausted() throws Exception {
        when(primaryProvider.fetch("videoId")).thenThrow(new QuotaExhaustedException("youtube-legacy"));
        when(secondaryProvider.fetch("videoId")).thenReturn("supadata transcript");

        String result = orchestrator.fetch("videoId");

        assertThat(result).isEqualTo("supadata transcript");
        verify(quotaService, never()).recordFetch("youtube-legacy");
        verify(quotaService).recordFetch("supadata");
    }

    @Test
    @DisplayName("should fall through to secondary provider on ProviderUnavailableException")
    void shouldFallThroughOnProviderUnavailable() throws Exception {
        when(primaryProvider.fetch("videoId")).thenThrow(new ProviderUnavailableException("youtube-legacy", "timeout"));
        when(secondaryProvider.fetch("videoId")).thenReturn("supadata transcript");

        String result = orchestrator.fetch("videoId");

        assertThat(result).isEqualTo("supadata transcript");
        verify(quotaService).recordFetch("supadata");
    }

    @Test
    @DisplayName("should return empty string when all providers fail")
    void shouldReturnEmptyWhenAllProvidersFail() throws Exception {
        when(primaryProvider.fetch("videoId")).thenThrow(new ProviderUnavailableException("youtube-legacy", "error"));
        when(secondaryProvider.fetch("videoId")).thenThrow(new ProviderUnavailableException("supadata", "error"));

        String result = orchestrator.fetch("videoId");

        assertThat(result).isEmpty();
        verify(quotaService, never()).recordFetch(anyString());
    }

    @Test
    @DisplayName("should skip disabled providers")
    void shouldSkipDisabledProviders() throws Exception {
        when(secondaryProvider.isEnabled()).thenReturn(false);
        when(primaryProvider.fetch("videoId")).thenReturn("transcript");

        orchestrator = new TranscriptOrchestrator(providerInstance, List.of("youtube-legacy", "supadata"), quotaService, registry);
        String result = orchestrator.fetch("videoId");

        assertThat(result).isEqualTo("transcript");
        verify(secondaryProvider, never()).fetch(anyString());
    }

    @Test
    @DisplayName("should record quota upsert only on success")
    void shouldRecordQuotaOnlyOnSuccess() throws Exception {
        when(primaryProvider.fetch("videoId")).thenThrow(new QuotaExhaustedException("youtube-legacy"));
        when(secondaryProvider.fetch("videoId")).thenThrow(new ProviderUnavailableException("supadata", "error"));

        orchestrator.fetch("videoId");

        verify(quotaService, never()).recordFetch(anyString());
    }
}
