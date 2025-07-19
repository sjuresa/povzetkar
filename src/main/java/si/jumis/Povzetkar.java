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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Orchestrates the main application workflows based on use cases.
 */
public class Povzetkar {
    private static final Logger logger = LoggerFactory.getLogger(Povzetkar.class);
    public static final String AUTHORS_TO_QUERY_FILENAME = "authors_to_query.txt";
    public static final String URLS_TO_SUMMARIZE_FILENAME = "videos_to_summarize.txt";
    public static final DateTimeFormatter DMY_DATE_FORMAT = DateTimeFormatter.ofPattern("dd.M.yyyy");
    private static final DateTimeFormatter YYYY_FORMAT = DateTimeFormatter.ofPattern("yyyy");
    private static final DateTimeFormatter MM_FORMAT = DateTimeFormatter.ofPattern("MM");

    private static final Path SUMMARIES_DIR = Paths.get("summaries");
    private static final Path BRIEFINGS_BASE_DIR = Paths.get("tldrly", "briefings");

    private final VideoSummarizationService summarizationService;
    private final VideoSummary videoSummary;
    private final ResourceReader resourceReader;

    private record ParsedTranscript(String title, String url, String author, String content) {}

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
        LocalDate date = LocalDate.of(2025, 7, 19);

        switch (useCase.toUpperCase()) {
            case "S_B" -> runSummarizeAndBriefWorkflow(date, dryRun);
            case "TR" -> runSummarizeTranscriptWorkflow(date);
            case "S_URL" -> runSummarizeFromUrlsWorkflow(date, dryRun);
            default -> logger.warn("Unknown use case: '{}'. No action will be taken.", useCase);
        }
    }

    void runSummarizeAndBriefWorkflow(LocalDate date, boolean dryRun) {
        List<String> authorsToSummarize = resourceReader.readLines(AUTHORS_TO_QUERY_FILENAME, "authors");
        String summaryFileName = "summaries_" + date.format(DMY_DATE_FORMAT) + ".md";
        Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);

        summarizationService.summarizeFromAuthors(authorsToSummarize, date, date, dryRun, summaryFilePath);

        if (!dryRun) {
            createBriefingFromFileUseCase(date, summaryFilePath);
        }
    }

    void runSummarizeFromUrlsWorkflow(LocalDate date, boolean dryRun) {
        List<String> urlsToSummarize = resourceReader.readLines(URLS_TO_SUMMARIZE_FILENAME, "URLs");
        if (!urlsToSummarize.isEmpty()) {
            String dateString = date.format(DMY_DATE_FORMAT);
            String summaryFileName = "summaries_" + dateString + ".md";
            Path summaryFilePath = SUMMARIES_DIR.resolve(summaryFileName);
            summarizationService.summarizeFromUrls(urlsToSummarize, dateString, dryRun, summaryFilePath);
        } else {
            logger.warn("URL file '{}' is empty or not found. Skipping URL summarization.", URLS_TO_SUMMARIZE_FILENAME);
        }
    }

    void runSummarizeTranscriptWorkflow(LocalDate date) {
        Path transcriptFilePath = Paths.get("transcript.txt");
        logger.info("Starting transcript summarization from file: {}", transcriptFilePath);
        try {
            Optional<ParsedTranscript> parsedTranscriptOpt = parseTranscriptFile(transcriptFilePath);
            if (parsedTranscriptOpt.isEmpty()) {
                return; // Logging is handled inside the parse method
            }
            ParsedTranscript parsed = parsedTranscriptOpt.get();

            String dateString = date.format(DMY_DATE_FORMAT);
            String summary = summarizationService.summarizeFromTranscript(
                    parsed.content(), parsed.url(), parsed.title(), parsed.author(), dateString);

            String safeTitle = parsed.title().replaceAll("[^a-zA-Z0-9.\\-]", "_").replaceAll("\\s+", "_");
            String outputFilename = "summary_from_transcript_" + safeTitle + "_" + dateString + ".md";
            Path outputFilePath = SUMMARIES_DIR.resolve(outputFilename);

            FileUtils.saveToFile(outputFilePath, summary);
            logger.info("Successfully saved transcript summary to: {}", outputFilePath);

        } catch (NoSuchFileException e) {
            logger.warn("Transcript file not found, skipping transcript summarization: {}. This is not an error.", transcriptFilePath);
        } catch (IOException e) {
            logger.error("An error occurred during transcript summarization for file: {}", transcriptFilePath, e);
        }
    }

    private Optional<ParsedTranscript> parseTranscriptFile(Path transcriptFilePath) throws IOException {
        List<String> lines = Files.readAllLines(transcriptFilePath, StandardCharsets.UTF_8);

        if (lines.size() < 4) {
            logger.warn("Transcript file '{}' is too short to contain a title, URL, author, and content. Skipping.", transcriptFilePath);
            return Optional.empty();
        }

        String videoTitle = lines.get(0).trim();
        String videoUrl = lines.get(1).trim();
        String author = lines.get(2).trim();
        String transcriptContent = lines.stream().skip(3).collect(Collectors.joining("\n")).trim();

        if (transcriptContent.isEmpty()) {
            logger.warn("Transcript content is empty in '{}', skipping.", transcriptFilePath);
            return Optional.empty();
        }

        return Optional.of(new ParsedTranscript(videoTitle, videoUrl, author, transcriptContent));
    }

    private void createBriefingFromFileUseCase(LocalDate date, Path summaryFilePath) {
        try {
            String content = Files.readString(summaryFilePath);
            logger.info("Creating daily briefing from the content of '{}'...", summaryFilePath);

            String dateString = date.format(DMY_DATE_FORMAT);
            String briefing = videoSummary.createBriefingFromSummaries(content, dateString);

            // Construct the structured path for the briefing
            Path briefingDir = BRIEFINGS_BASE_DIR
                    .resolve(date.format(YYYY_FORMAT))
                    .resolve(date.format(MM_FORMAT));

            Files.createDirectories(briefingDir); // Ensure the directory exists

            Path briefingFilePath = briefingDir.resolve("daily_briefing_" + dateString + ".md");

            FileUtils.saveToFile(briefingFilePath, briefing);
            logger.info("Successfully saved daily briefing to: {}", briefingFilePath);

        } catch (NoSuchFileException e) {
            logger.warn("Summaries file '{}' not found, so no briefing will be created. This is expected if no videos were processed.", summaryFilePath);
        } catch (IOException e) {
            logger.error("Error processing briefing from {}.", summaryFilePath, e);
        }
    }
}