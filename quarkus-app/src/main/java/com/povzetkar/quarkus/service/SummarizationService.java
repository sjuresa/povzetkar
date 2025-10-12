package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class SummarizationService {
    private static final Logger logger = LoggerFactory.getLogger(SummarizationService.class);
    private final ContentCreationService contentCreationService;
    private final VideoSummaryService videoSummaryService;

    public SummarizationService(ContentCreationService contentCreationService, VideoSummaryService videoSummaryService) {
        this.contentCreationService = contentCreationService;
        this.videoSummaryService = videoSummaryService;
    }

    @Transactional
    public boolean summarizeVideo(ContentItem item) {
        if (!(item instanceof YouTubeVideo video)) {
            logger.error("Summarization attempted on an unsupported content item type: {}", item.getClass().getName());
            item.processingStatus = ProcessingStatus.FAILED;
            return false;
        }
        Summary existingSummary = Summary.findByContentId(video.id);
        if (existingSummary != null) {
            return true;
        }
        Content content = contentCreationService.createContentForVideo(video);
        if (content == null) {
            // This case happens when transcript retrieval fails.
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
}
