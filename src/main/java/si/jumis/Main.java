package si.jumis;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.GenAiPool;
import si.jumis.ai.VideoSummary;
import si.jumis.service.DefaultFileWriter;
import si.jumis.service.FileWriter;
import si.jumis.service.ProcessingStateService;
import si.jumis.service.TimeTextFormatter;
import si.jumis.service.VideoSummarizationService;
import si.jumis.service.YouTubeDataService;
import si.jumis.service.YouTubeTranscriptService;
import si.jumis.util.ResourceReader;
import si.jumis.util.Sleeper;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static si.jumis.Povzetkar.DMY_DATE_FORMAT;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long COOLDOWN_PERIOD_NS = TimeUnit.SECONDS.toNanos(60);
    public static final String YOUTUBE_APP_NAME = "gemini-test-app";

    public static void main(String[] args) {
        String youtubeApiKey = System.getenv("YOUTUBE_API_KEY");
        if (youtubeApiKey == null || youtubeApiKey.isBlank()) {
            logger.error("YOUTUBE_API_KEY environment variable not set. Exiting.");
            return;
        }

        String googleApiKeysEnv = System.getenv("GOOGLE_API_KEYS");
        if (googleApiKeysEnv == null || googleApiKeysEnv.isBlank()) {
            logger.error("GOOGLE_API_KEYS environment variable not set. Exiting.");
            return;
        }
        List<String> googleApiKeys = new java.util.ArrayList<>(Arrays.asList(googleApiKeysEnv.split(",")));
        googleApiKeys.removeIf(String::isBlank);
        if (googleApiKeys.isEmpty()) {
            logger.error("GOOGLE_API_KEYS environment variable is set but contains no valid keys. Exiting.");
            return;
        }
        String timezoneId = System.getenv().getOrDefault("APP_TIMEZONE", "Europe/Ljubljana");

        RunConfiguration runConfiguration = parseRunConfiguration(args);
        try (GenAiPool genAiPool = new GenAiPool(googleApiKeys, COOLDOWN_PERIOD_NS, new Sleeper.DefaultSleeper(), new GenAiPool.DefaultTimeProvider())) {
            Povzetkar povzetkar = getPovzetkar(genAiPool, youtubeApiKey, ZoneId.of(timezoneId));
            povzetkar.run(runConfiguration);
        }
    }

    @NotNull
    private static Povzetkar getPovzetkar(GenAiPool genAiPool, String apiKey, ZoneId zoneId) {
        VideoSummary videoSummary = new VideoSummary(genAiPool);
        ProcessingStateService stateService = new ProcessingStateService();
        YouTubeDataService youtubeDataService = new YouTubeDataService(apiKey,
                new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), null)
                        .setApplicationName(YOUTUBE_APP_NAME).build(), zoneId);
        YouTubeTranscriptService transcriptService = new YouTubeTranscriptService(new TimeTextFormatter());
        FileWriter fileWriter = new DefaultFileWriter();
        ResourceReader resourceReader = new ResourceReader();
        VideoSummarizationService summarizationService = new VideoSummarizationService(youtubeDataService, stateService,
                videoSummary, transcriptService, fileWriter, Clock.system(zoneId), resourceReader);

        return new Povzetkar(summarizationService, videoSummary, resourceReader);
    }

    /**
     * Parses command-line arguments to determine the application's run configuration.
     * Defaults to useCase="S_B" and date=today if no arguments are provided.
     *
     * @param args Command-line arguments. Supports --use-case=[S_B|S_URL] and --date=d.M.yyyy.
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
