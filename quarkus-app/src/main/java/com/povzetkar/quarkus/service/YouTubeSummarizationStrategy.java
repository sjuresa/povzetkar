package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class YouTubeSummarizationStrategy implements SummarizationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeSummarizationStrategy.class);

    private final ContentCreationService contentCreationService;
    private final VideoSummaryService videoSummaryService;

    @Inject
    public YouTubeSummarizationStrategy(ContentCreationService contentCreationService, VideoSummaryService videoSummaryService) {
        this.contentCreationService = contentCreationService;
        this.videoSummaryService = videoSummaryService;
    }

    @Override
    public boolean summarize(ContentItem item) {
        YouTubeVideo video = (YouTubeVideo) item;
        Summary existingSummary = Summary.findByContentId(video.id);
        if (existingSummary != null) {
            return true;
        }

        Content content = contentCreationService.createContentForVideo(video);
        if (content == null) {
            video.processingStatus = ProcessingStatus.RETRY_LATER;
            return false;
        }

        try {
            String summaryText = videoSummaryService.generateContentWithRetry(content, "video summary");
            if (summaryText == null) {
                video.processingStatus = ProcessingStatus.RETRY_LATER;
                return false;
            }
            Summary summary = new Summary(summaryText, video);
            summary.persist();
            video.processingStatus = ProcessingStatus.COMPLETED;
            return true;
        } catch (ApiException e) {
            logger.error("Failed to create summary for video: {}.", video.sourceUrl, e);
            video.processingStatus = ProcessingStatus.FAILED;
            logger.error("Video will be marked as failed to avoid retries.");
            return false;
        }
    }

    @Override
    public boolean supports(ContentItem item) {
        return item instanceof YouTubeVideo;
    }
}
