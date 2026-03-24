package com.povzetkar.quarkus.service;

import com.google.genai.errors.ApiException;
import com.google.genai.types.Content;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentProcessingError;
import com.povzetkar.quarkus.model.JobExecutionAudit;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.util.LogKeys;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.hibernate.exception.ConstraintViolationException;

import java.time.LocalDate;

@ApplicationScoped
@Priority(10)
public class YouTubeSummarizationStrategy implements SummarizationStrategy {

    private static final StructuredLogger logger = StructuredLogger.getLogger(YouTubeSummarizationStrategy.class);

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

        if (isAlreadySummarized(video.id)) {
            return true;
        }

        Content content = contentCreationService.createContentForVideo(video);
        if (content == null) {
            updateStatusInNewTransaction(video.id, video.title, ProcessingStatus.RETRY_LATER, ContentProcessingError.TRANSCRIPT_UNAVAILABLE, "Transcript could not be retrieved.");
            return false;
        }

        try {
            String summaryText = generateSummary(client, video, content);
            persistSummary(video, summaryText, orchestrationDate);
            logger.info(
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.SUCCESS,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title
            );
            return true;
        } catch (PersistenceException e) {
            return handlePersistenceException(video, e);
        } catch (ApiException e) {
            return handleApiException(video, e);
        }
    }

    private boolean isAlreadySummarized(Long videoId) {
        return QuarkusTransaction.requiringNew().call(() -> Summary.findByContentId(videoId) != null);
    }

    private String generateSummary(GenAiClient client, YouTubeVideo video, Content content) {
        logger.info(
            LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
            LogKeys.VIDEO_ID, video.videoId,
            LogKeys.VIDEO_TITLE, video.title,
            LogKeys.DURATION, video.formattedDuration(),
            LogKeys.URL, video.sourceUrl
        );
        String description = String.format("video summary for '%s' (ID: %s)", video.title, video.videoId);
        return videoSummaryService.generateContent(client, content, description, video.title, video.videoId);
    }

    private void persistSummary(YouTubeVideo video, String summaryText, LocalDate orchestrationDate) {
        QuarkusTransaction.requiringNew().run(() -> {
            YouTubeVideo attachedVideo = YouTubeVideo.findById(video.id);
            if (attachedVideo != null) {
                // Explicitly copy the transcript from the detached object to the managed entity
                attachedVideo.transcript = video.transcript;

                Summary summary = new Summary(summaryText, attachedVideo, orchestrationDate);
                summary.persist();
                attachedVideo.processingStatus = ProcessingStatus.COMPLETED;
                // Clear any previous errors on success
                attachedVideo.errorCode = null;
                attachedVideo.errorMessage = null;

                // POV-150: Audit item completion
                JobExecutionAudit.logItem(attachedVideo.id, attachedVideo.title, ProcessingStatus.COMPLETED, "Summary generated successfully.");
            } else {
                logger.warn(
                    LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                    LogKeys.STATUS, LogKeys.FAILED,
                    LogKeys.VIDEO_ID, video.videoId,
                    LogKeys.REASON, "video_not_found"
                );
            }
        });
    }

    private boolean handlePersistenceException(YouTubeVideo video, PersistenceException e) {
        if (e.getCause() instanceof ConstraintViolationException) {
            logger.info(
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.SKIPPED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.REASON, "race_condition"
            );
            return true; // Treat as success as the summary likely exists now.
        } else {
            logger.error(e,
                LogKeys.ACTION, LogKeys.PERSIST_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
            updateStatusInNewTransaction(video.id, video.title, ProcessingStatus.FAILED, ContentProcessingError.UNKNOWN_ERROR, e.getMessage());
            return false;
        }
    }

    private boolean handleApiException(YouTubeVideo video, ApiException e) {
        ContentProcessingError error;

        if (e.code() == 429) {
            error = ContentProcessingError.RATE_LIMIT_EXCEEDED;
        } else if (e.code() >= 500) {
            error = ContentProcessingError.UPSTREAM_SERVICE_ERROR;
        } else {
            error = ContentProcessingError.UNKNOWN_ERROR;
        }

        ProcessingStatus status = error.isRetriable() ? ProcessingStatus.RETRY_LATER : ProcessingStatus.FAILED;

        if (error.isRetriable()) {
            logger.warn(
                LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.ERROR_CODE, e.code(),
                LogKeys.REASON, "retriable_error"
            );
        } else {
            logger.error(e,
                LogKeys.ACTION, LogKeys.GENERATE_SUMMARY,
                LogKeys.STATUS, LogKeys.FAILED,
                LogKeys.VIDEO_ID, video.videoId,
                LogKeys.VIDEO_TITLE, video.title,
                LogKeys.ERROR_CODE, e.code(),
                LogKeys.ERROR_MESSAGE, e.getMessage()
            );
        }
        
        updateStatusInNewTransaction(video.id, video.title, status, error, e.getMessage());
        return false;
    }

    private void updateStatusInNewTransaction(Long videoId, String title, ProcessingStatus status, ContentProcessingError errorCode, String errorMessage) {
        QuarkusTransaction.requiringNew().run(() -> {
            ContentItem item = ContentItem.findById(videoId);
            if (item != null) {
                item.processingStatus = status;
                if (errorCode != null) {
                    item.errorCode = errorCode.name();
                    item.errorMessage = errorMessage;
                }
                // POV-150: Audit item failure or retry
                JobExecutionAudit.logItem(videoId, title, status, errorMessage);
            }
        });
    }
}
