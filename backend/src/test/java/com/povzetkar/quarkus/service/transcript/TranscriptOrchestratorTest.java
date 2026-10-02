package com.povzetkar.quarkus.service.transcript;

import com.povzetkar.quarkus.service.ProcessingContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.enterprise.inject.Instance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private static final Instant T0 = Instant.parse("2026-10-02T09:27:02Z");
    private final AtomicReference<Instant> now = new AtomicReference<>(T0);
    private final Clock clock = new Clock() {
        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now.get();
        }
    };
    private final ProcessingContext context = ProcessingContext.automatic(T0, Duration.ofMinutes(15));
    private TranscriptOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        when(primaryProvider.name()).thenReturn("youtube-legacy");
        when(primaryProvider.isEnabled()).thenReturn(true);
        when(secondaryProvider.name()).thenReturn("supadata");
        when(secondaryProvider.isEnabled()).thenReturn(true);

        when(providerInstance.spliterator()).thenAnswer(inv -> List.<TranscriptProvider>of(primaryProvider, secondaryProvider).spliterator());

        orchestrator = new TranscriptOrchestrator(providerInstance, List.of("youtube-legacy", "supadata"), quotaService, registry, clock);
    }

    @Test
    @DisplayName("should return transcript from first provider when it succeeds")
    void shouldReturnTranscriptFromFirstProvider() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenReturn("transcript text");

        String result = orchestrator.fetch("videoId", context);

        assertThat(result).isEqualTo("transcript text");
        verify(secondaryProvider, never()).fetch(anyString(), any());
        verify(quotaService).recordFetch("youtube-legacy");
    }

    @Test
    @DisplayName("should fall through to secondary provider on QuotaExhaustedException")
    void shouldFallThroughOnQuotaExhausted() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenThrow(new QuotaExhaustedException("youtube-legacy"));
        when(secondaryProvider.fetch("videoId", context)).thenReturn("supadata transcript");

        String result = orchestrator.fetch("videoId", context);

        assertThat(result).isEqualTo("supadata transcript");
        verify(quotaService, never()).recordFetch("youtube-legacy");
        verify(quotaService).recordFetch("supadata");
    }

    @Test
    @DisplayName("should fall through to secondary provider on ProviderUnavailableException")
    void shouldFallThroughOnProviderUnavailable() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenThrow(new ProviderUnavailableException("youtube-legacy", "timeout"));
        when(secondaryProvider.fetch("videoId", context)).thenReturn("supadata transcript");

        String result = orchestrator.fetch("videoId", context);

        assertThat(result).isEqualTo("supadata transcript");
        verify(quotaService).recordFetch("supadata");
    }

    @Test
    @DisplayName("should return empty string when all providers fail")
    void shouldReturnEmptyWhenAllProvidersFail() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenThrow(new ProviderUnavailableException("youtube-legacy", "error"));
        when(secondaryProvider.fetch("videoId", context)).thenThrow(new ProviderUnavailableException("supadata", "error"));

        String result = orchestrator.fetch("videoId", context);

        assertThat(result).isEmpty();
        verify(quotaService, never()).recordFetch(anyString());
    }

    @Test
    @DisplayName("should skip disabled providers")
    void shouldSkipDisabledProviders() throws Exception {
        when(secondaryProvider.isEnabled()).thenReturn(false);
        when(primaryProvider.fetch("videoId", context)).thenReturn("transcript");

        orchestrator = new TranscriptOrchestrator(providerInstance, List.of("youtube-legacy", "supadata"), quotaService, registry, clock);
        String result = orchestrator.fetch("videoId", context);

        assertThat(result).isEqualTo("transcript");
        verify(secondaryProvider, never()).fetch(anyString(), any());
    }

    @Test
    @DisplayName("a skipped fetch (time budget used up) propagates: no fall-through, no failure metric")
    void skippedFetchPropagatesWithoutFallThrough() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenThrow(new TranscriptSkippedException("budget used up"));

        assertThatThrownBy(() -> orchestrator.fetch("videoId", context)).isInstanceOf(TranscriptSkippedException.class);

        verify(secondaryProvider, never()).fetch(anyString(), any());
        assertThat(registry.find("transcript.fetch.failures").counters()).isEmpty();
        verify(quotaService, never()).recordFetch(anyString());
    }

    @Test
    @DisplayName("time budget already used up: skipped before any provider is asked")
    void budgetUsedUpSkipsBeforeAnyProvider() throws Exception {
        now.set(T0.plus(Duration.ofMinutes(16)));

        assertThatThrownBy(() -> orchestrator.fetch("videoId", context)).isInstanceOf(TranscriptSkippedException.class);

        verify(primaryProvider, never()).fetch(anyString(), any());
        verify(secondaryProvider, never()).fetch(anyString(), any());
    }

    @Test
    @DisplayName("time budget used up while the first provider failed: no fall-through to the next provider")
    void budgetUsedUpDuringFirstProviderStopsFallThrough() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenAnswer(inv -> {
            now.set(T0.plus(Duration.ofMinutes(16)));
            throw new ProviderUnavailableException("youtube-legacy", "Status code: 429");
        });

        assertThatThrownBy(() -> orchestrator.fetch("videoId", context)).isInstanceOf(TranscriptSkippedException.class);

        verify(secondaryProvider, never()).fetch(anyString(), any());
    }

    @Test
    @DisplayName("manual fetch uses each provider's manual path, never the automatic one")
    void manualFetchUsesManualPath() throws Exception {
        when(primaryProvider.fetchManual("videoId")).thenReturn("manual transcript");

        String result = orchestrator.fetchManual("videoId");

        assertThat(result).isEqualTo("manual transcript");
        verify(primaryProvider, never()).fetch(anyString(), any());
        verify(quotaService).recordFetch("youtube-legacy");
        assertThat(registry.counter("transcript.fetch.successes", "provider", "youtube-legacy").count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("manual fetch falls through the chain like the automatic one")
    void manualFetchFallsThrough() throws Exception {
        when(primaryProvider.fetchManual("videoId")).thenThrow(new ProviderUnavailableException("youtube-legacy", "429"));
        when(secondaryProvider.fetchManual("videoId")).thenReturn("supadata transcript");

        String result = orchestrator.fetchManual("videoId");

        assertThat(result).isEqualTo("supadata transcript");
        assertThat(registry.counter("transcript.fetch.failures", "provider", "youtube-legacy", "reason", "unavailable").count())
                .isEqualTo(1.0);
    }

    @Test
    @DisplayName("should record quota upsert only on success")
    void shouldRecordQuotaOnlyOnSuccess() throws Exception {
        when(primaryProvider.fetch("videoId", context)).thenThrow(new QuotaExhaustedException("youtube-legacy"));
        when(secondaryProvider.fetch("videoId", context)).thenThrow(new ProviderUnavailableException("supadata", "error"));

        orchestrator.fetch("videoId", context);

        verify(quotaService, never()).recordFetch(anyString());
    }
}
