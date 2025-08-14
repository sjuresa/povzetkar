package si.jumis;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.GenAiPool;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.service.ProcessingStateService;
import si.jumis.service.TimeTextFormatter;
import si.jumis.service.VideoSummarizationService;
import si.jumis.service.YouTubeTranscriptService;
import si.jumis.util.ResourceReader;

import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

import static si.jumis.Povzetkar.DMY_DATE_FORMAT;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long COOLDOWN_PERIOD_NS = TimeUnit.SECONDS.toNanos(60);

    public static void main(String[] args) {
        String apiKey = System.getenv("YOUTUBE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("YOUTUBE_API_KEY environment variable not set. Exiting.");
            return;
        }
        RunConfiguration runConfiguration = parseRunConfiguration(args);
        GenAiPool genAiPool = new GenAiPool(System.getenv("GOOGLE_API_KEY"),
                System.getenv("GOOGLE_API_KEY1"), COOLDOWN_PERIOD_NS);
        Povzetkar povzetkar = getPovzetkar(genAiPool, apiKey);
        povzetkar.run(runConfiguration);
    }

    @NotNull
    private static Povzetkar getPovzetkar(GenAiPool genAiPool, String apiKey) {
        VideoSummary videoSummary = new VideoSummary(genAiPool);
        ProcessingStateService stateService = new ProcessingStateService();
        YouTubeDataService youtubeDataService = new YouTubeDataService(apiKey);
        VideoSummarizationService summarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary, new YouTubeTranscriptService(new TimeTextFormatter()));
        ResourceReader resourceReader = new ResourceReader();

        return new Povzetkar(summarizationService, videoSummary, resourceReader);
    }

    /**
     * Parses command-line arguments to determine the application's run configuration.
     * Defaults to useCase="S_B" and date=today if no arguments are provided.
     *
     * @param args Command-line arguments. Supports --use-case=[S_B|TR|S_URL] and --date=d.M.yyyy.
     * @return A {@link RunConfiguration} object.
     */
    private static RunConfiguration parseRunConfiguration(String[] args) {
        String useCase = "S_B"; // Default use case
        LocalDate date = LocalDate.now(); // Default date
        boolean dryRun = false; // Default dryRun

        for (String arg : args) {
            if (arg.toLowerCase().startsWith("--use-case=")) {
                useCase = arg.substring("--use-case=".length()).trim();
            } else if (arg.toLowerCase().startsWith("--date=")) {
                String dateStr = arg.substring("--date=".length()).trim();
                try {
                    date = LocalDate.parse(dateStr, DMY_DATE_FORMAT);
                } catch (java.time.format.DateTimeParseException e) {
                    logger.error("Invalid date format for --date argument: '{}'. Please use 'd.M.yyyy'. Exiting.", dateStr);
                    System.exit(1);
                }
            } else if (arg.equalsIgnoreCase("--dry-run")) {
                dryRun = true;
            }
        }
        RunConfiguration config = new RunConfiguration(useCase, date, dryRun);
        logger.info("Running with configuration: {}", config);
        return config;
    }
}