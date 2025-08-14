package si.jumis.service;

import io.github.thoroldvix.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class YouTubeTranscriptService {

    private static final Logger log = LoggerFactory.getLogger(YouTubeTranscriptService.class);

    public String getTranscript(String videoId) {
        if (videoId == null || videoId.isBlank()) {
            log.warn("getTranscript called with a null or blank video ID.");
            return "";
        }
        try {
            YoutubeTranscriptApi youtubeTranscriptApi = TranscriptApiFactory.createDefault();
            TranscriptContent transcriptContent = youtubeTranscriptApi.listTranscripts(videoId)
                    .findTranscript()
                    .fetch();

            TranscriptFormatter formatter = new TimeTextFormatter();
            return formatter.format(transcriptContent);
        } catch (IllegalArgumentException e) {
            log.warn("The provided video ID '{}' has an invalid format.", videoId, e);
            return "";
        } catch (TranscriptRetrievalException e) {
            log.error("Could not retrieve transcript for video ID '{}'. The video may be unavailable or private.", videoId, e);
            return "";
        } catch (Exception e) {
            log.error("An unexpected error occurred while processing video ID '{}'.", videoId, e);
            return "";
        }
    }
}
