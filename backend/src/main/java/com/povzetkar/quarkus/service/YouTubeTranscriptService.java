package com.povzetkar.quarkus.service;

import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptFormatter;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.povzetkar.quarkus.model.MetricErrorType;

@ApplicationScoped
public class YouTubeTranscriptService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeTranscriptService.class);
    private final TranscriptFormatter formatter;
    private final YoutubeTranscriptApi youtubeTranscriptApi;
    private final MeterRegistry registry;

    @Inject
    public YouTubeTranscriptService(MeterRegistry registry) {
        this(new TimeTextFormatter(), TranscriptApiFactory.createDefault(), registry);
    }

    YouTubeTranscriptService(TranscriptFormatter formatter, YoutubeTranscriptApi youtubeTranscriptApi, MeterRegistry registry) {
        this.formatter = formatter;
        this.youtubeTranscriptApi = youtubeTranscriptApi;
        this.registry = registry;
    }

    public String getTranscript(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            log.warn("getTranscript called with a null or blank video ID.");
            return "";
        }

        Timer.Sample sample = Timer.start(registry);
        try {
            TranscriptContent transcriptContent = youtubeTranscriptApi.listTranscripts(videoId)
                    .findTranscript()
                    .fetch();

            String result = formatter.format(transcriptContent);
            sample.stop(registry.timer("povzetkar_transcript_retrieval_duration_seconds"));
            return result;
        } catch (IllegalArgumentException e) {
            registry.counter("povzetkar_transcript_service_failed_total", "error_type", MetricErrorType.INVALID_FORMAT).increment();
            log.warn("The provided video ID '{}' has an invalid format.", videoId, e);
            return "";
        } catch (TranscriptRetrievalException e) {
            registry.counter("povzetkar_transcript_service_failed_total", "error_type", MetricErrorType.RETRIEVAL_EXCEPTION).increment();
            log.error("Could not retrieve transcript for video ID '{}'. The video may be unavailable or private.", videoId, e);
            return "";
        }
    }
}
