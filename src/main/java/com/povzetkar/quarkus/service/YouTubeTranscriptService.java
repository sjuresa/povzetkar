package com.povzetkar.quarkus.service;

import io.github.thoroldvix.api.TranscriptApiFactory;
import io.github.thoroldvix.api.TranscriptContent;
import io.github.thoroldvix.api.TranscriptFormatter;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeTranscriptApi;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class YouTubeTranscriptService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeTranscriptService.class);
    private final TranscriptFormatter formatter;
    private final YoutubeTranscriptApi youtubeTranscriptApi;

    public YouTubeTranscriptService() {
        this(new TimeTextFormatter());
    }

    YouTubeTranscriptService(TranscriptFormatter formatter) {
        this(formatter, TranscriptApiFactory.createDefault());
    }

    YouTubeTranscriptService(TranscriptFormatter formatter, YoutubeTranscriptApi youtubeTranscriptApi) {
        this.formatter = formatter;
        this.youtubeTranscriptApi = youtubeTranscriptApi;
    }

    public String getTranscript(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            log.warn("getTranscript called with a null or blank video ID.");
            return "";
        }
        try {
            TranscriptContent transcriptContent = youtubeTranscriptApi.listTranscripts(videoId)
                    .findTranscript()
                    .fetch();

            return formatter.format(transcriptContent);
        } catch (IllegalArgumentException e) {
            log.warn("The provided video ID '{}' has an invalid format.", videoId, e);
            return "";
        } catch (TranscriptRetrievalException e) {
            log.error("Could not retrieve transcript for video ID '{}'. The video may be unavailable or private.", videoId, e);
            return "";
        }
    }
}
