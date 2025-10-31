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
    private final int transcriptThresholdMinutes;

    private final YouTubeTranscriptService youTubeTranscriptService;

    public ContentCreationService(YouTubeTranscriptService youTubeTranscriptService,
                                  @ConfigProperty(name = "transcript.threshold.minutes") int threshold) {
        this.youTubeTranscriptService = youTubeTranscriptService;
        this.transcriptThresholdMinutes = threshold;
    }

    public Content createContentForVideo(YouTubeVideo video) {
        if (video.duration.toMinutes() < transcriptThresholdMinutes) {
            Part textPart = createVideoSummaryTextPart(video, "");
            return Content.fromParts(textPart, Part.fromUri(video.sourceUrl, "video/mp4"));
        }

        logger.info("Video is longer than {} minutes. Getting transcript.", transcriptThresholdMinutes);
        String transcript = youTubeTranscriptService.getTranscript(video.videoId);

        if (transcript.isBlank()) {
            logger.warn("Could not retrieve transcript for video {}. It will be skipped for now.", video.sourceUrl);
            return null;
        }

        logger.info("Got transcript successfully for video {}.", video.sourceUrl);
        video.transcript = transcript;
        Part textPart = createVideoSummaryTextPart(video, transcript);
        return Content.fromParts(textPart);
    }

    private Part createVideoSummaryTextPart(YouTubeVideo video, String transcript) {
        String sourceType = transcript.isBlank() ? "video url" : "video transcript";
        String prompt = Prompts.VIDEO_SUMMARY_PROMPT.formatted(
                sourceType,
                video.sourceUrl,
                video.title,
                video.sourceUrl,
                video.authorName,
                video.formattedPublishedAt(),
                video.formattedDuration(),
                transcript
        );
        return Part.fromText(prompt);
    }
}
