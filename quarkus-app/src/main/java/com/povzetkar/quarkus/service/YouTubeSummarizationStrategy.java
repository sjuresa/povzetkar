package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class YouTubeSummarizationStrategy implements SummarizationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(YouTubeSummarizationStrategy.class);
    private static final String UPDATE_STATUS_QUERY = "processingStatus = ?1 where id = ?2";

    private final ContentCreationService contentCreationService;
    private final VideoSummaryService videoSummaryService;

    @Inject
    public YouTubeSummarizationStrategy(ContentCreationService contentCreationService, VideoSummaryService videoSummaryService) {
        this.contentCreationService = contentCreationService;
        this.videoSummaryService = videoSummaryService;
    }

    @Override
    public boolean supports(ContentItem item) {
        return item instanceof YouTubeVideo;
    }

    @Override
    public boolean summarize(ContentItem item) {
        YouTubeVideo video = (YouTubeVideo) item;

        boolean alreadySummarized = QuarkusTransaction.requiringNew().call(() -> Summary.findByContentId(video.id) != null);
        if (alreadySummarized) {
            return true;
        }

        Content content = contentCreationService.createContentForVideo(video);
        if (content == null) {
            QuarkusTransaction.requiringNew().run(() -> ContentItem.update(UPDATE_STATUS_QUERY, ProcessingStatus.RETRY_LATER, video.id));
            return false;
        }

        try {
            String summaryText = videoSummaryService.generateContentWithRetry(content, "video summary");
            if (summaryText == null) {
                QuarkusTransaction.requiringNew().run(() -> ContentItem.update(UPDATE_STATUS_QUERY, ProcessingStatus.RETRY_LATER, video.id));
                return false;
            }

            QuarkusTransaction.requiringNew().run(() -> {
                ContentItem attachedItem = ContentItem.findById(video.id);
                if (attachedItem != null) {
                    Summary summary = new Summary(summaryText, attachedItem);
                    summary.persist();
                    attachedItem.processingStatus = ProcessingStatus.COMPLETED;
                }
            });
            return true;
        } catch (ApiException e) {
            logger.error("Failed to create summary for video: {}.", video.sourceUrl, e);
            QuarkusTransaction.requiringNew().run(() -> ContentItem.update(UPDATE_STATUS_QUERY, ProcessingStatus.FAILED, video.id));
            logger.error("Video will be marked as failed to avoid retries.");
            return false;
        }
    }
}
