package com.povzetkar.quarkus.service.transcript;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
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

    @Inject
    public TranscriptOrchestrator(
            @Any Instance<TranscriptProvider> providers,
            @ConfigProperty(name = "transcript.providers.order", defaultValue = "youtube-legacy,supadata") List<String> providerOrder,
            TranscriptProviderQuotaService quotaService,
            MeterRegistry registry) {
        this.quotaService = quotaService;
        this.registry = registry;
        this.orderedProviders = buildOrderedChain(providers, providerOrder);
    }

    public String fetch(String videoId) {
        for (TranscriptProvider provider : orderedProviders) {
            registry.counter(METRIC_ATTEMPTS, TAG_PROVIDER, provider.name()).increment();
            Timer.Sample sample = Timer.start(registry);
            try {
                String transcript = provider.fetch(videoId);
                sample.stop(registry.timer(METRIC_DURATION, TAG_PROVIDER, provider.name()));
                registry.counter(METRIC_SUCCESSES, TAG_PROVIDER, provider.name()).increment();
                quotaService.recordFetch(provider.name());
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

    private List<TranscriptProvider> buildOrderedChain(Instance<TranscriptProvider> providers, List<String> order) {
        Map<String, TranscriptProvider> byName = StreamSupport.stream(providers.spliterator(), false)
                .collect(Collectors.toMap(TranscriptProvider::name, Function.identity()));

        return order.stream()
                .map(String::trim)
                .map(byName::get)
                .filter(p -> p != null && p.isEnabled())
                .collect(Collectors.toList());
    }
}
