package com.povzetkar.quarkus.service.transcript;

import com.povzetkar.quarkus.service.ProcessingContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

@ApplicationScoped
public class TranscriptOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(TranscriptOrchestrator.class);

    private static final String METRIC_ATTEMPTS = "transcript.fetch.attempts";
    private static final String METRIC_SUCCESSES = "transcript.fetch.successes";
    private static final String METRIC_FAILURES = "transcript.fetch.failures";
    private static final String METRIC_DURATION = "transcript.fetch.duration";
    private static final String TAG_PROVIDER = "provider";
    private static final String TAG_REASON = "reason";
    private static final String REASON_QUOTA = "quota_exhausted";
    private static final String REASON_UNAVAILABLE = "unavailable";

    private final List<TranscriptProvider> orderedProviders;
    private final TranscriptProviderQuotaService quotaService;
    private final MeterRegistry registry;
    private final Clock clock;

    @Inject
    public TranscriptOrchestrator(
            @Any Instance<TranscriptProvider> providers,
            @ConfigProperty(name = "transcript.providers.order", defaultValue = "youtube-legacy,supadata") List<String> providerOrder,
            TranscriptProviderQuotaService quotaService,
            MeterRegistry registry,
            Clock clock) {
        this.quotaService = quotaService;
        this.registry = registry;
        this.clock = clock;
        this.orderedProviders = buildOrderedChain(providers, providerOrder);
    }

    /**
     * Automatic fetch (orchestration runs, per-item jobs): paced providers wait for their slot within the context's
     * time budget.
     *
     * @throws TranscriptSkippedException the time budget was used up before a provider could be tried, or a provider
     *                                    did not attempt the fetch — passed on unchanged, without a failure metric and
     *                                    without trying the next provider (POV-222)
     */
    public String fetch(String videoId, ProcessingContext context) {
        return fetchVia(videoId, (provider, id) -> provider.fetch(id, context), provider -> {
            if (context.isPastDeadline(clock.instant())) {
                throw new TranscriptSkippedException(
                        "Orchestration time budget used up before trying provider '" + provider.name() + "'.");
            }
        });
    }

    /** Manual transcript capture (admin "Fetch transcript"): exempt from pacing (POV-222). */
    public String fetchManual(String videoId) {
        return fetchVia(videoId, TranscriptProvider::fetchManual, _ -> { });
    }

    private String fetchVia(String videoId, ProviderCall call, Consumer<TranscriptProvider> beforeProvider) {
        for (TranscriptProvider provider : orderedProviders) {
            beforeProvider.accept(provider);
            registry.counter(METRIC_ATTEMPTS, TAG_PROVIDER, provider.name()).increment();
            Timer.Sample sample = Timer.start(registry);
            try {
                String transcript = call.fetch(provider, videoId);
                sample.stop(registry.timer(METRIC_DURATION, TAG_PROVIDER, provider.name()));
                registry.counter(METRIC_SUCCESSES, TAG_PROVIDER, provider.name()).increment();
                recordQuotaBestEffort(provider.name());
                log.info("Transcript fetched successfully via provider '{}' for video ID '{}'.", provider.name(), videoId);
                return transcript;
            } catch (QuotaExhaustedException _) {
                sample.stop(registry.timer(METRIC_DURATION, TAG_PROVIDER, provider.name()));
                registry.counter(METRIC_FAILURES, TAG_PROVIDER, provider.name(), TAG_REASON, REASON_QUOTA).increment();
                log.warn("Provider '{}' quota exhausted for video ID '{}', trying next provider.", provider.name(), videoId);
            } catch (ProviderUnavailableException e) {
                sample.stop(registry.timer(METRIC_DURATION, TAG_PROVIDER, provider.name()));
                registry.counter(METRIC_FAILURES, TAG_PROVIDER, provider.name(), TAG_REASON, REASON_UNAVAILABLE).increment();
                log.warn("Provider '{}' unavailable for video ID '{}': {}. Trying next provider.", provider.name(), videoId, e.getMessage());
            }
        }
        log.error("All transcript providers failed for video ID '{}'.", videoId);
        return "";
    }

    @FunctionalInterface
    private interface ProviderCall {
        String fetch(TranscriptProvider provider, String videoId) throws QuotaExhaustedException, ProviderUnavailableException;
    }

    private void recordQuotaBestEffort(String providerName) {
        try {
            quotaService.recordFetch(providerName);
        } catch (Exception e) {
            log.error("Failed to record quota usage for provider '{}'.", providerName, e);
        }
    }

    private List<TranscriptProvider> buildOrderedChain(Instance<TranscriptProvider> providers, List<String> order) {
        Map<String, TranscriptProvider> byName = StreamSupport.stream(providers.spliterator(), false)
                .collect(Collectors.toMap(TranscriptProvider::name, Function.identity(), (a, b) -> {
                    log.warn("Duplicate TranscriptProvider bean registered for name '{}'; keeping first.", a.name());
                    return a;
                }));

        return order.stream()
                .map(String::trim)
                .distinct()
                .map(byName::get)
                .filter(p -> p != null && p.isEnabled())
                .collect(Collectors.toList());
    }
}
