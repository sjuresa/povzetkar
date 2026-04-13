package com.povzetkar.quarkus.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.ai.Prompts;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@ApplicationScoped
public class ContentCreationService {

    private static final StructuredLogger logger = StructuredLogger.getLogger(ContentCreationService.class);
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{(.+?)}}");
    private final int transcriptThresholdMinutes;

    private final YouTubeTranscriptService youTubeTranscriptService;

    public ContentCreationService(YouTubeTranscriptService youTubeTranscriptService,
                                  @ConfigProperty(name = "transcript.threshold.minutes") int threshold) {
        this.youTubeTranscriptService = youTubeTranscriptService;
        this.transcriptThresholdMinutes = threshold;
    }

    public Content createContentForVideo(YouTubeVideo video) {
        String transcript = video.transcript;
        
        // 1. Try existing transcript
        if (transcript != null && !transcript.isBlank()) {
            logger.info(
                LogKeys.ACTION, LogKeys.CREATE_CONTENT,
                LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.SOURCE, "existing_transcript",
                LogKeys.MESSAGE, "Using existing transcript"
            );
            Part textPart = createVideoSummaryTextPart(video, transcript);
            return Content.fromParts(textPart);
        }

        // 2. Always attempt to fetch transcript first
        logger.info(
            LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
            LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.MESSAGE, "Attempting to fetch transcript"
        );
        transcript = youTubeTranscriptService.getTranscript(video.videoId);

        if (transcript != null && !transcript.isBlank()) {
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

        // 3. Fallback for short videos if transcript is missing
        if (video.duration.toMinutes() < transcriptThresholdMinutes) {
            logger.info(
                LogKeys.ACTION, LogKeys.CREATE_CONTENT,
                LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.SOURCE, "metadata_only",
                LogKeys.MESSAGE, "Video is shorter than threshold and transcript is missing; using metadata only."
            );
            // We do NOT use Part.fromUri with a YouTube URL as it causes 403 errors in Gemini.
            // Multimodal processing requires direct file access or GCS/File API upload.
            Part textPart = createVideoSummaryTextPart(video, "");
            return Content.fromParts(textPart);
        }

        logger.warn(
            LogKeys.ACTION, LogKeys.GET_TRANSCRIPT,
            LogKeys.ENTITY_TYPE, LogKeys.VIDEO,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.STATUS, "failure",
            LogKeys.MESSAGE, "Could not retrieve transcript for long video"
        );
        return null;
    }

    private Part createVideoSummaryTextPart(YouTubeVideo video, String transcript) {
        String safeTranscript = transcript != null ? transcript : "";
        String sourceType = safeTranscript.isBlank() ? "video url" : "video transcript";
        String template = Prompts.VIDEO_SUMMARY_PROMPT;

        String finalPrompt;
        if (template.contains("{{")) {
            Map<String, String> plainValues = Map.of(
                    "sourceType", sourceType,
                    "sourceUrl", video.sourceUrl != null ? video.sourceUrl : "",
                    "title", video.title != null ? video.title : "",
                    "author", video.authorName != null ? video.authorName : "",
                    "publishedAt", video.formattedPublishedAt(),
                    "duration", video.formattedDuration(),
                    "transcript", safeTranscript
            );

            Matcher matcher = VARIABLE_PATTERN.matcher(template);
            StringBuilder sb = new StringBuilder();

            while (matcher.find()) {
                String key = matcher.group(1);
                // Fallback to original placeholder if key not found in map
                String value = plainValues.getOrDefault(key, matcher.group(0));
                matcher.appendReplacement(sb, Matcher.quoteReplacement(value));
            }
            matcher.appendTail(sb);
            finalPrompt = sb.toString();
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
                    safeTranscript
            );
        }
        return Part.fromText(finalPrompt);
    }
}
