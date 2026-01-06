package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@ApplicationScoped
@Priority(10)
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
    public boolean supports(ContentItem item) {
        return item instanceof YouTubeVideo;
    }

    @Override
    public boolean summarize(GenAiClient client, ContentItem item, LocalDate orchestrationDate) {
        YouTubeVideo video = (YouTubeVideo) item;

        boolean alreadySummarized = QuarkusTransaction.requiringNew().call(() -> Summary.findByContentId(video.id) != null);
        if (alreadySummarized) {
            return true;
        }

        Content content = contentCreationService.createContentForVideo(video);
        if (content == null) {
            updateStatusInNewTransaction(video.id, ProcessingStatus.RETRY_LATER);
            return false;
        }

        try {
            logger.info("Generating summary for video: {} (ID: {})", video.title, video.id);
            String callDescription = String.format("video summary for '%s' (ID: %d)", video.title, video.id);
            String summaryText = videoSummaryService.generateContent(client, content, callDescription);
            
            QuarkusTransaction.requiringNew().run(() -> {
                ContentItem attachedItem = ContentItem.findById(video.id);
                if (attachedItem != null) {
                    Summary summary = new Summary(summaryText, attachedItem, orchestrationDate);
                    summary.persist();
                    attachedItem.processingStatus = ProcessingStatus.COMPLETED;
                }
            });
            logger.info("Successfully generated and persisted summary for video: {} (ID: {})", video.title, video.id);
            return true;
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.info("Could not persist summary for video id {}, likely due to a race condition. Assuming it was created by another process.", video.id);
                return true; // Treat as success as the summary likely exists now.
            } else {
                logger.error("Persistence error while creating summary for video: {}.", video.sourceUrl, e);
                updateStatusInNewTransaction(video.id, ProcessingStatus.FAILED);
                return false;
            }
        } catch (ApiException e) {
            if (e.code() == 429) {
                logger.warn("Rate limit exceeded (429) for video: {} (ID: {}). Marking as RETRY_LATER. Error: {}", video.title, video.id, e.getMessage());
                updateStatusInNewTransaction(video.id, ProcessingStatus.RETRY_LATER);
            } else {
                logger.error("Failed to create summary for video: {} (ID: {}). Error code: {}. Message: {}", video.title, video.id, e.code(), e.getMessage(), e);
                updateStatusInNewTransaction(video.id, ProcessingStatus.FAILED);
            }
            return false;
        }
    }

    private void updateStatusInNewTransaction(Long videoId, ProcessingStatus status) {
        QuarkusTransaction.requiringNew().run(() ->
                ContentItem.updateProcessingStatus(videoId, status)
        );
    }
}
