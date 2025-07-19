package si.jumis.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.model.YouTubeVideo;
import si.jumis.util.FileUtils;
import si.jumis.util.Sleeper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Orchestrates different video summarization workflows.
 */
public class VideoSummarizationService {
    private static final Logger logger = LoggerFactory.getLogger(VideoSummarizationService.class);

    private record Author(String name, String channelId) {}
    private record VideoToProcess(YouTubeVideo video, String channelId) {}

    private final YouTubeDataService youtubeDataService;
    private final ProcessingStateService stateService;
    private final VideoSummary videoSummary;
    private final Sleeper sleeper;

    public VideoSummarizationService(YouTubeDataService youtubeDataService, ProcessingStateService stateService, VideoSummary videoSummary) {
        this(youtubeDataService, stateService, videoSummary, new Sleeper.DefaultSleeper());
    }

    /**
     * Constructor for dependency injection, allowing a custom sleeper for testing.
     */
    VideoSummarizationService(YouTubeDataService youtubeDataService, ProcessingStateService stateService, VideoSummary videoSummary, Sleeper sleeper) {
        this.youtubeDataService = youtubeDataService;
        this.stateService = stateService;
        this.videoSummary = videoSummary;
        this.sleeper = sleeper;
    }

    /**
     * Fetches all new videos from a list of authors, sorts them, then summarizes them.
     * Manages state to avoid re-processing videos.
     */
    public void summarizeFromAuthors(List<String> authorLines, LocalDate fromDate, LocalDate toDate, boolean dryRun, Path outputFilePath) {
        logger.info("Starting new video summary workflow for {} author(s) from {} to {}...", authorLines.size(), fromDate, toDate);

        List<Author> authors = parseAuthors(authorLines);
        if (authors.isEmpty()) {
            logger.warn("No valid authors found to process.");
            return;
        }

        List<VideoToProcess> allNewVideos = fetchAllNewVideos(authors, fromDate, toDate);

        if (allNewVideos.isEmpty()) {
            logger.info("No new videos found for any author in the specified period.");
            return;
        }

        // Sort the collected videos chronologically before processing.
        allNewVideos.sort(Comparator.comparing(v -> v.video().publishedAt()));
        logger.info("A total of {} new videos found and sorted. Generating summaries...", allNewVideos.size());

        summarizeVideoBatch(allNewVideos, fromDate, toDate, dryRun, outputFilePath);
    }

    private List<Author> parseAuthors(List<String> authorLines) {
        List<Author> authors = new ArrayList<>();
        for (String authorLine : authorLines) {
            String[] parts = authorLine.split(",", 2);
            if (parts.length == 2 && !parts[0].trim().isEmpty() && !parts[1].trim().isEmpty()) {
                authors.add(new Author(parts[0].trim(), parts[1].trim()));
            } else {
                logger.warn("Skipping malformed author line: '{}'", authorLine);
            }
        }
        return authors;
    }

    private List<VideoToProcess> fetchAllNewVideos(List<Author> authors, LocalDate fromDate, LocalDate toDate) {
        List<VideoToProcess> allNewVideos = new ArrayList<>();
        for (Author author : authors) {
            try {
                logger.info("--- Checking for new videos from {} ---", author.name());
                String lastVideoId = stateService.findLastProcessedVideoIdBefore(author.channelId(), fromDate);
                List<YouTubeVideo> fetchedVideos = fetchVideosForAuthor(author, fromDate, lastVideoId);
                List<YouTubeVideo> videosInDateRange = filterVideosByDate(fetchedVideos, fromDate, toDate);

                logger.info("Found {} new videos for {} in the period.", videosInDateRange.size(), author.name());
                videosInDateRange.forEach(video -> allNewVideos.add(new VideoToProcess(video, author.channelId())));

            } catch (IOException e) {
                logger.error("Failed to fetch videos for author '{}'. Skipping this author.", author.name(), e);
            }
        }
        return allNewVideos;
    }

    private List<YouTubeVideo> fetchVideosForAuthor(Author author, LocalDate fromDate, String lastVideoId) throws IOException {
        if (lastVideoId == null) {
            logger.info("No previous state found for {} on or before {}. Fetching recent videos to establish initial state.", author.name(), fromDate);
            return youtubeDataService.findVideosByAuthor(author.channelId(), fromDate, 50L);
        } else {
            logger.info("Found last processed video ID: {}. Fetching all newer videos since then.", lastVideoId);
            return youtubeDataService.findNewVideosSince(author.channelId(), lastVideoId);
        }
    }

    /**
     * Processes a pre-sorted batch of videos, handling file creation and looping.
     */
    private void summarizeVideoBatch(List<VideoToProcess> sortedVideos, LocalDate fromDate, LocalDate toDate, boolean dryRun, Path outputFilePath) {
        String promptDateString = formatDateRange(fromDate, toDate);

        try {
            prepareOutputDirectory(outputFilePath, dryRun);
        } catch (IOException e) {
            logger.error("Failed to prepare output file directory for {}. Aborting summarization.", outputFilePath, e);
            return;
        }

        try {
            boolean fileInitiallyHadContent = !dryRun && FileUtils.fileExists(outputFilePath) && FileUtils.fileHasContent(outputFilePath);
            String separator;

            for (int i = 0; i < sortedVideos.size(); i++) {
                VideoToProcess videoToProcess = sortedVideos.get(i);
                YouTubeVideo video = videoToProcess.video();

                logger.info("({}/{}) Summarizing: [{}]({}) by {}, {}", i + 1, sortedVideos.size(), video.title(), video.url(), video.authorName(), video.publishedAt());

                if (dryRun) {
                    logger.info("DRY RUN: Skipping summary creation and state update for video: {}", video.title());
                } else {
                    if (i == 0 && !fileInitiallyHadContent) {
                        separator = "";
                    } else {
                        separator = "\n\n---\n\n";
                    }
                    processSingleVideo(videoToProcess, outputFilePath, promptDateString, toDate, separator);
                }
                pauseBetweenRequests(i, sortedVideos.size(), dryRun);
            }
        } catch (IOException e) {
            logger.error("An error occurred while writing summaries to file: {}", outputFilePath, e);
        }

        logger.info("Author summarization workflow complete.");
    }

    /**
     * Handles the summarization, file writing, and state update for a single video.
     * If summarization fails, it logs the error and still updates the processing state.
     */
    private void processSingleVideo(VideoToProcess videoToProcess, Path outputFilePath, String promptDateString, LocalDate processingDate, String separator) {
        YouTubeVideo video = videoToProcess.video();
        try {
            String summary = videoSummary.createSummary(video.url(), promptDateString);
            FileUtils.appendToFile(outputFilePath, separator + summary);
        } catch (Exception e) {
            handleSummaryFailure(video, e);
        } finally {
            updateProcessingState(videoToProcess, processingDate);
        }
    }

    private void handleSummaryFailure(YouTubeVideo video, Exception e) {
        logger.error("Failed to create summary for video: {}. It will be marked as processed to avoid retries.", video.url(), e);
        try {
            String failureMessage = String.format("Video URL: %s%nError: %s%n---%n", video.url(), e.getMessage());
            FileUtils.appendToFile(Paths.get("failed_videos.log"), failureMessage);
        } catch (IOException logEx) {
            logger.error("Additionally, failed to write to the failure log for video: {}", video.url(), logEx);
        }
    }

    private void updateProcessingState(VideoToProcess videoToProcess, LocalDate processingDate) {
        YouTubeVideo video = videoToProcess.video();
        String channelId = videoToProcess.channelId();
        try {
            stateService.updateLastProcessedVideoForDate(channelId, video, processingDate);
            stateService.saveState("Updated after processing video ID: " + video.id() + " for date " + processingDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));
        } catch (IOException stateEx) {
            logger.error("CRITICAL: Failed to update processing state for video: {}. This may cause reprocessing.", video.url(), stateEx);
        }
    }

    /**
     * Creates summaries from a list of video URLs and saves them to a file.
     */
    public void summarizeFromUrls(List<String> videoUrls, String date, boolean dryRun, Path outputFilePath) {
        logger.info("Preparing to generate {} summaries from URLs and save to {}", videoUrls.size(), outputFilePath);

        try {
            prepareOutputDirectory(outputFilePath, dryRun);

            boolean fileInitiallyHadContent = !dryRun && FileUtils.fileExists(outputFilePath) && FileUtils.fileHasContent(outputFilePath);

            for (int i = 0; i < videoUrls.size(); i++) {
                String url = videoUrls.get(i);
                logger.info("({}/{}) Processing URL: {}", i + 1, videoUrls.size(), url);
                if (!dryRun) {
                    String summary = videoSummary.createSummary(url, date);
                    String contentToAppend;
                    if (i == 0 && !fileInitiallyHadContent) {
                        contentToAppend = summary; // First item to an empty/new file, no leading separator.
                    } else {
                        contentToAppend = "\n\n---\n\n" + summary; // Any subsequent item, or first item to a non-empty file.
                    }
                    FileUtils.appendToFile(outputFilePath, contentToAppend);
                }
                pauseBetweenRequests(i, videoUrls.size(), dryRun);
            }
            logger.info("URL summarization complete.");
        } catch (IOException e) {
            logger.error("An error occurred while writing summaries to file: {}", outputFilePath, e);
        }
    }

    /**
     * Creates a summary directly from a video's transcript text.
     */
    public String summarizeFromTranscript(String transcript, String videoUrl, String videoTitle, String author, String date) {
        logger.info("Generating summary from transcript for video: {}", videoTitle);
        return videoSummary.createSummaryFromTranscript(transcript, videoUrl, videoTitle, author, date);
    }

    private void prepareOutputDirectory(Path outputFilePath, boolean dryRun) throws IOException {
        if (dryRun) {
            return;
        }
        Path parentDir = outputFilePath.getParent();
        if (parentDir != null) {
            Files.createDirectories(parentDir);
        }
    }

    private List<YouTubeVideo> filterVideosByDate(List<YouTubeVideo> videos, LocalDate fromDate, LocalDate toDate) {
        Instant startInstant = fromDate.atStartOfDay(ZoneId.of("UTC")).toInstant();
        Instant endInstant = toDate.plusDays(1).atStartOfDay(ZoneId.of("UTC")).toInstant();
        return videos.stream()
                .filter(v -> !v.publishedAt().isBefore(startInstant) && v.publishedAt().isBefore(endInstant))
                .toList();
    }

    private String formatDateRange(LocalDate fromDate, LocalDate toDate) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.M.yyyy");
        return fromDate.equals(toDate) ? fromDate.format(formatter) : String.format("%s%s%s", fromDate.format(formatter), " - ", toDate.format(formatter));
    }

    private void pauseBetweenRequests(int currentIndex, int totalSize, boolean dryRun) {
        long sleepMillis = 60_000;
        if (currentIndex < totalSize - 1 && !dryRun) {
            logger.info("Waiting for {} seconds before processing the next item...", sleepMillis / 1000);
            try {
                sleeper.sleep(sleepMillis);
            } catch (InterruptedException e) {
                logger.warn("Sleep timer was interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }
    }
}