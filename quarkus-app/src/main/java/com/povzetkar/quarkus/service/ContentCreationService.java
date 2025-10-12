package com.povzetkar.quarkus.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.ai.Prompts;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ContentCreationService {

    private static final Logger logger = LoggerFactory.getLogger(ContentCreationService.class);
    private int transcriptThresholdMinutes;

    private final YouTubeTranscriptService youTubeTranscriptService;

    public ContentCreationService(YouTubeTranscriptService youTubeTranscriptService,
                                  @ConfigProperty(name = "transcript.threshold.minutes") int threshold) {
        this.youTubeTranscriptService = youTubeTranscriptService;
        this.transcriptThresholdMinutes = threshold;
    }

    public Content createContentForVideo(YouTubeVideo video) {
        if (video.duration.toMinutes() < transcriptThresholdMinutes) {
            return Content.fromParts(
                    Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video url", video.sourceUrl,
                            video.title, video.sourceUrl, video.authorName,
                            video.formattedPublishedAt(), video.formattedDuration(), "")),
                    Part.fromUri(video.sourceUrl, "video/mp4"));
        }
        logger.info("Video is longer than {} minutes. Getting transcript.", transcriptThresholdMinutes);
        String transcript = youTubeTranscriptService.getTranscript(video.videoId);
        if (transcript.isBlank()) {
            logger.warn("Could not retrieve transcript for video {}. It will be skipped for now.", video.sourceUrl);
            return null;
        }
        logger.info("Got transcript successfully for video {}.", video.sourceUrl);
        video.transcript = transcript;
        return Content.fromParts(
                Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video transcript", video.sourceUrl,
                        video.title, video.sourceUrl, video.authorName,
                        video.formattedPublishedAt(), video.formattedDuration(), transcript)));
    }
}
