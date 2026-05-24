package com.povzetkar.quarkus.service.transcript;

import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptContent.Fragment;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

@ApplicationScoped
public class YouTubeLegacyTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(YouTubeLegacyTranscriptProvider.class);
    private static final String PROVIDER_NAME = "youtube-legacy";

    private final YoutubeTranscriptApi youtubeTranscriptApi;
    private final TimeTextFormatter formatter;
    private final boolean enabled;
    private final int jitterMinMs;
    private final int jitterMaxMs;

    @Inject
    public YouTubeLegacyTranscriptProvider(
            @ConfigProperty(name = "transcript.providers.youtube-legacy.enabled", defaultValue = "true") boolean enabled,
            @ConfigProperty(name = "transcript.providers.youtube-legacy.jitter.min-ms", defaultValue = "500") int jitterMinMs,
            @ConfigProperty(name = "transcript.providers.youtube-legacy.jitter.max-ms", defaultValue = "2500") int jitterMaxMs) {
        this(TranscriptApiFactory.createDefault(), enabled, jitterMinMs, jitterMaxMs, new TimeTextFormatter());
    }

    YouTubeLegacyTranscriptProvider(YoutubeTranscriptApi youtubeTranscriptApi, boolean enabled, int jitterMinMs, int jitterMaxMs, TimeTextFormatter formatter) {
        this.youtubeTranscriptApi = youtubeTranscriptApi;
        this.formatter = formatter;
        this.enabled = enabled;
        this.jitterMinMs = jitterMinMs;
        this.jitterMaxMs = jitterMaxMs;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String fetch(String videoId) throws ProviderUnavailableException {
        applyJitter();

        try {
            TranscriptContent transcriptContent = youtubeTranscriptApi.listTranscripts(videoId)
                    .findTranscript()
                    .fetch();

            List<TranscriptSegment> segments = toSegments(transcriptContent.getContent());
            return formatter.format(segments);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid video ID format '{}' for YouTube legacy provider.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        } catch (TranscriptRetrievalException e) {
            log.warn("Could not retrieve transcript for video ID '{}' via YouTube legacy provider.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        }
    }

    private void applyJitter() {
        if (jitterMinMs <= 0 && jitterMaxMs <= 0) {
            return;
        }
        int range = Math.max(1, jitterMaxMs - jitterMinMs);
        int delay = jitterMinMs + ThreadLocalRandom.current().nextInt(range);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private List<TranscriptSegment> toSegments(List<Fragment> fragments) {
        return fragments.stream()
                .map(f -> new TranscriptSegment(f.getStart(), Optional.ofNullable(f.getText()).orElse("")))
                .toList();
    }
}
