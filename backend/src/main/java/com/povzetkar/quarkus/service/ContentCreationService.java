package com.povzetkar.quarkus.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.ai.Prompts;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class ContentCreationService {

    private static final StructuredLogger logger = StructuredLogger.getLogger(ContentCreationService.class);
    private final int transcriptThresholdMinutes;

    private final YouTubeTranscriptService youTubeTranscriptService;

    public ContentCreationService(YouTubeTranscriptService youTubeTranscriptService,
                                  @ConfigProperty(name = "transcript.threshold.minutes") int threshold) {
        this.youTubeTranscriptService = youTubeTranscriptService;
        this.transcriptThresholdMinutes = threshold;
    }

    public Content createContentForVideo(YouTubeVideo video) {
        if (video.transcript != null && !video.transcript.isBlank()) {
            logger.info(
                LogKeys.ACTION, LogKeys.CREATE_CONTENT,
                LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.SOURCE, "existing_transcript",
                LogKeys.MESSAGE, "Using existing transcript"
            );
            Part textPart = createVideoSummaryTextPart(video, video.transcript);
            return Content.fromParts(textPart);
        }

        if (video.duration.toMinutes() < transcriptThresholdMinutes) {
            logger.info(
                LogKeys.ACTION, LogKeys.CREATE_CONTENT,
                LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.SOURCE, "video_url",
                LogKeys.MESSAGE, "Video is shorter than threshold, using URL"
            );
            Part textPart = createVideoSummaryTextPart(video, "");
            return Content.fromParts(textPart, Part.fromUri(video.sourceUrl, "video/mp4"));
        }

        logger.info(
            LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
            LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.MESSAGE, "Video is longer than threshold, getting transcript"
        );
        String transcript = youTubeTranscriptService.getTranscript(video.videoId);

        if (transcript.isBlank()) {
            logger.warn(
                LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
                LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.STATUS, "failure",
                LogKeys.MESSAGE, "Could not retrieve transcript"
            );
            return null;
        }

        logger.info(
            LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
            LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.STATUS, "success",
            LogKeys.MESSAGE, "Got transcript successfully"
        );
        video.transcript = transcript;
        Part textPart = createVideoSummaryTextPart(video, transcript);
        return Content.fromParts(textPart);
    }

    private Part createVideoSummaryTextPart(YouTubeVideo video, String transcript) {
        String sourceType = transcript.isBlank() ? "video url" : "video transcript";
        String template = Prompts.VIDEO_SUMMARY_PROMPT;

        String finalPrompt;
        if (template.contains("{{")) {
            finalPrompt = template
                    .replace("{{sourceType}}", sourceType)
                    .replace("{{sourceUrl}}", video.sourceUrl != null ? video.sourceUrl : "")
                    .replace("{{title}}", video.title != null ? video.title : "")
                    .replace("{{author}}", video.authorName != null ? video.authorName : "")
                    .replace("{{publishedAt}}", video.formattedPublishedAt())
                    .replace("{{duration}}", video.formattedDuration())
                    .replace("{{transcript}}", transcript != null ? transcript : "");
        } else {
            // Fallback to legacy index-based formatting if template isn't migrated
            finalPrompt = template.formatted(
                    sourceType,
                    video.sourceUrl,
                    video.title,
                    video.sourceUrl,
                    video.authorName,
                    video.formattedPublishedAt(),
                    video.formattedDuration(),
                    transcript
            );
        }
        return Part.fromText(finalPrompt);
    }
}
