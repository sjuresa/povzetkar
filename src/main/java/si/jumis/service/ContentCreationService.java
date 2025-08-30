package si.jumis.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.Prompts;
import si.jumis.model.YouTubeVideo;

public class ContentCreationService {

    private static final Logger logger = LoggerFactory.getLogger(ContentCreationService.class);
    private static final int TRANSCRIPT_THRESHOLD_MINUTES = 50;

    private final YouTubeTranscriptService youTubeTranscriptService;

    public ContentCreationService(YouTubeTranscriptService youTubeTranscriptService) {
        this.youTubeTranscriptService = youTubeTranscriptService;
    }

    public Content createContentForVideo(YouTubeVideo video) {
        if (video.duration().toMinutes() <= TRANSCRIPT_THRESHOLD_MINUTES) {
            return Content.fromParts(
                    Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video url", video.url(),
                            video.title(), video.url(), video.authorName(),
                            video.formattedPublishedAt(), video.formattedDuration(), "")),
                    Part.fromUri(video.url(), "video/mp4"));
        }
        logger.info("Video is longer than {} minutes. Getting transcript.", TRANSCRIPT_THRESHOLD_MINUTES);
        String transcript = youTubeTranscriptService.getTranscript(video.id());
        if (transcript.isBlank()) {
            logger.warn("Could not retrieve transcript for video {}. It will be skipped for now.", video.url());
            return null;
        }
        logger.info("Got transcript successfully for video {}.", video.url());
        return Content.fromParts(
                Part.fromText(Prompts.VIDEO_SUMMARY_PROMPT.formatted("video transcript", video.url(),
                        video.title(), video.url(), video.authorName(),
                        video.formattedPublishedAt(), video.formattedDuration(), transcript)));
    }
}
