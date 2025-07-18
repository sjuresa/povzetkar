package si.jumis;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.FileUtils;
import si.jumis.util.ResourceReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Orchestrates the main application workflows based on use cases.
 */
public class Povzetkar {
    private static final Logger logger = LoggerFactory.getLogger(Povzetkar.class);
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";
    public static final String URLS_TO_SUMMARIZE_FILENAME = "videos_to_summarize.txt";
    public static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.M.yyyy");

    private final VideoSummarizationService summarizationService;
    private final VideoSummary videoSummary;
    private final ResourceReader resourceReader;

    public Povzetkar(VideoSummarizationService summarizationService, VideoSummary videoSummary, ResourceReader resourceReader) {
        this.summarizationService = summarizationService;
        this.videoSummary = videoSummary;
        this.resourceReader = resourceReader;
    }

    /**
     * Runs the application workflow based on the specified use case.
     *
     * @param useCase The workflow to execute (S_B, TR, S_URL).
     * @param dryRun  If true, simulates the run without making external calls or writing files.
     */
    public void run(String useCase, boolean dryRun) {
        LocalDate date = LocalDate.of(2025, 7, 18);

        switch (useCase.toUpperCase()) {
            case "S_B" -> runSummarizeAndBriefWorkflow(date, dryRun);
            case "TR" -> runSummarizeTranscriptWorkflow(date);
            case "S_URL" -> runSummarizeFromUrlsWorkflow(date, dryRun);
            default -> logger.warn("Unknown use case: '{}'. No action will be taken.", useCase);
        }
    }

    void runSummarizeAndBriefWorkflow(LocalDate date, boolean dryRun) {
        List<String> authorsToSummarize = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
        summarizationService.summarizeFromAuthors(authorsToSummarize, date, date, dryRun);
        if (!dryRun) {
            createBriefingFromFileUseCase(date.format(DMY_DATE_FORMAT));
        }
    }

    void runSummarizeFromUrlsWorkflow(LocalDate date, boolean dryRun) {
        List<String> urlsToSummarize = resourceReader.readLines(URLS_TO_SUMMARIZE_FILENAME, "URLs");
        if (!urlsToSummarize.isEmpty()) {
            String dateString = date.format(DMY_DATE_FORMAT);
            summarizationService.summarizeFromUrls(urlsToSummarize, dateString, false, dryRun);
        } else {
            logger.warn("URL file '{}' is empty or not found. Skipping URL summarization.", URLS_TO_SUMMARIZE_FILENAME);
        }
    }

    void runSummarizeTranscriptWorkflow(LocalDate date) {
        String transcriptFilePath = "transcript.txt";
        logger.info("Starting transcript summarization from file: {}", transcriptFilePath);
        try {
            List<String> lines = Files.readAllLines(Paths.get(transcriptFilePath), StandardCharsets.UTF_8);

            if (lines.size() < 4) {
                logger.warn("Transcript file '{}' is too short to contain a title, URL, author, and content. Skipping.", transcriptFilePath);
                return;
            }

            String videoTitle = lines.get(0).trim();
            String videoUrl = lines.get(1).trim();
            String author = lines.get(2).trim();
            String transcript = lines.stream().skip(3).collect(Collectors.joining("\n")).trim();

            if (transcript.isEmpty()) {
                logger.warn("Transcript content is empty in '{}', skipping.", transcriptFilePath);
                return;
            }

            String dateString = date.format(DMY_DATE_FORMAT);
            String summary = summarizationService.summarizeFromTranscript(transcript, videoUrl, videoTitle, author, dateString);

            String safeTitle = videoTitle.replaceAll("[^a-zA-Z0-9.\\-]", "_").replaceAll("\\s+", "_");
            String outputFilename = "summary_from_transcript_" + safeTitle + "_" + dateString + ".md";
            FileUtils.saveToFile(outputFilename, summary);
            logger.info("Successfully saved transcript summary to: {}", outputFilename);
        } catch (NoSuchFileException e) {
            logger.warn("Transcript file not found, skipping transcript summarization: {}. This is not an error.", transcriptFilePath);
        } catch (IOException e) {
            logger.error("An error occurred during transcript summarization for file: {}", transcriptFilePath, e);
        }
    }

    private void createBriefingFromFileUseCase(String date) {
        String summariesFilename = "summaries_" + date + ".md";

        try {
            String content = Files.readString(Paths.get(summariesFilename));
            logger.info("Creating daily briefing from the content of '{}'...", summariesFilename);
            String briefing = videoSummary.createBriefingFromSummaries(content, date);
            FileUtils.saveToFile("daily_briefing_" + date + ".md", briefing);
        } catch (NoSuchFileException e) {
            logger.warn("Summaries file '{}' not found, so no briefing will be created. This is expected if no videos were processed.", summariesFilename);
        } catch (IOException e) {
            logger.error("Error reading from {} to create briefing.", summariesFilename, e);
        }
    }
}