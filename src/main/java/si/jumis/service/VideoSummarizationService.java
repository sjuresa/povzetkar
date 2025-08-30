package si.jumis.service;

import com.google.genai.types.Content;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.model.Author;
import si.jumis.model.YouTubeVideo;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates different video summarization workflows.
 */
public class VideoSummarizationService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummarizationService.class);

    private final ProcessingStateService stateService;
    private final VideoSummary videoSummary;
    private final FileWriter fileWriter;
    private final NewVideoFetcherService newVideoFetcherService;
    private final ContentCreationService contentCreationService;
    private final FailureLoggerService failureLoggerService;

    public VideoSummarizationService(ProcessingStateService stateService, VideoSummary videoSummary, FileWriter fileWriter, NewVideoFetcherService newVideoFetcherService, ContentCreationService contentCreationService, FailureLoggerService failureLoggerService) {
        this.stateService = stateService;
        this.videoSummary = videoSummary;
        this.fileWriter = fileWriter;
        this.newVideoFetcherService = newVideoFetcherService;
        this.contentCreationService = contentCreationService;
        this.failureLoggerService = failureLoggerService;
    }

    /**
     * Fetches all new videos from a list of authors, sorts them, then summarizes them.
     * Manages state to avoid re-processing videos.
     *
     * @return A list of the videos that were processed.
     */
    public List<YouTubeVideo> summarizeFromAuthors(List<Author> authors, LocalDate forDate,
                                                   Path outputFilePath, boolean dryRun) {
        logger.info("Starting new video summary workflow for {} author(s) for {}...", authors.size(), forDate);

        if (authors.isEmpty()) {
            logger.warn("No valid authors found to process.");
            return Collections.emptyList();
        }

        List<NewVideoFetcherService.VideoToProcess> allNewVideos = newVideoFetcherService.fetchAllNewVideos(authors, forDate);

        if (allNewVideos.isEmpty()) {
            logger.info("No new videos found for any author in the specified period.");
            return Collections.emptyList();
        }

        // Sort the collected videos chronologically before processing.
        allNewVideos.sort(Comparator.comparing(v -> v.video().publishedAt()));
        logger.info("A total of {} new videos found and sorted. Generating summaries...", allNewVideos.size());

        return summarizeVideoBatch(allNewVideos, forDate, outputFilePath, dryRun);
    }

    /**
     * Processes a pre-sorted batch of videos, handling file creation and looping.
     *
     * @return A list of the videos that were actually processed (not in dry run).
     */
    private List<YouTubeVideo> summarizeVideoBatch(List<NewVideoFetcherService.VideoToProcess> sortedVideos, LocalDate forDate,
                                                   Path outputFilePath, boolean dryRun) {
        List<YouTubeVideo> processedVideos = new ArrayList<>();
        SummaryFileAppender appender = null;
        if (!dryRun) {
            try {
                prepareOutputDirectory(outputFilePath);
                appender = new SummaryFileAppender(fileWriter, outputFilePath);
            } catch (IOException e) {
                logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
                return Collections.emptyList();
            }
        }

        for (int i = 0; i < sortedVideos.size(); i++) {
            NewVideoFetcherService.VideoToProcess videoToProcess = sortedVideos.get(i);
            YouTubeVideo video = videoToProcess.video();

            if (logger.isInfoEnabled()) {
                logger.info("({}/{}) Summarizing: [{}]({})\n  by {}, Published at: {}, duration: {}", i + 1,
                        sortedVideos.size(), video.title(), video.url(), video.authorName(),
                        video.formattedPublishedAt(), video.formattedDuration());
            }
            if (!dryRun && processSingleVideo(videoToProcess, appender, forDate)) {
                processedVideos.add(video);
            }
        }

        logger.info("Author summarization workflow complete.");
        return processedVideos;
    }

    /**
     * Handles the summarization, file writing, and state update for a single video.
     * If summarization fails, it logs the error and still updates the processing state.
     */
    private boolean processSingleVideo(NewVideoFetcherService.VideoToProcess videoToProcess, SummaryFileAppender appender,
                                       LocalDate processingDate) {
        YouTubeVideo video = videoToProcess.video();
        String summary;
        try {
            Content content = contentCreationService.createContentForVideo(video);
            if (content == null) {
                // This case happens when transcript retrieval fails. We log it but do not update the state,
                // allowing the system to retry fetching the transcript on the next run.
                failureLoggerService.logVideoSummaryFailure(video, new Exception("Transcript retrieval failed, content is null."));
                return false;
            }
            summary = videoSummary.generateContentWithRetry(content, "video summary");
        } catch (Exception e) {
            // This case handles failures in the AI summarization step. We log it and update the state
            // to prevent retrying a video that consistently fails to be summarized.
            failureLoggerService.logVideoSummaryFailure(video, e);
            logger.error("Video will be marked as processed to avoid retries.");
            updateProcessingState(videoToProcess, processingDate);
            return false;
        }

        try {
            appender.append(summary);
            updateProcessingState(videoToProcess, processingDate); // Only update state on full success
            return true;
        } catch (IOException e) {
            logger.error("Failed to write summary to file for video: {}. It will NOT be marked as processed.", video.url(), e);
            return false;
        }
    }

    private void updateProcessingState(NewVideoFetcherService.VideoToProcess videoToProcess, LocalDate processingDate) {
        YouTubeVideo video = videoToProcess.video();
        Author author = videoToProcess.author();
        try {
            stateService.updateLastProcessedVideoForDate(author.channelId(), video, processingDate);
            stateService.saveState("Updated after processing video ID: " + video.id() + " for date " + processingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        } catch (IOException stateEx) {
            logger.error("CRITICAL: Failed to update processing state for video: {}. This may cause reprocessing.", video.url(), stateEx);
        }
    }

    /**
     * Creates summaries from a list of video URLs and saves them to a file.
     */
    public void summarizeFromUrls(List<String> videoUrls, String date, Path outputFilePath) {
        logger.info("Preparing to generate {} summaries from URLs and save to {}", videoUrls.size(), outputFilePath);
        SummaryFileAppender appender;
        try {
            prepareOutputDirectory(outputFilePath);
            appender = new SummaryFileAppender(fileWriter, outputFilePath);
        } catch (IOException e) {
            logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
            return;
        }

        int count = 0;
        for (String url : videoUrls) {
            try {
                logger.info("({}/{}) Processing URL: {}", ++count, videoUrls.size(), url);
                String summary = videoSummary.createSummaryForURL(url, date);
                appender.append(summary);
            } catch (Exception e) {
                logger.error("Failed to process and write summary for URL: {}. Skipping.", url, e);
            }
        }
        logger.info("URL summarization complete.");
    }

    private void prepareOutputDirectory(Path outputFilePath) throws IOException {
        Path parentDir = outputFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
    }
}
