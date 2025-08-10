package si.jumis;

import com.google.genai.Client;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.service.ProcessingStateService;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.ResourceReader;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Holds the application's runtime configuration, parsed from command-line arguments.
     */
    private record RunConfiguration(String useCase, LocalDate date) {
    }

    public static void main(String[] args) {
        String apiKey = System.getenv("YOUTUBE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("YOUTUBE_API_KEY environment variable not set. Exiting.");
            return;
        }
        RunConfiguration runConfiguration = parseRunConfiguration(args);
        try (Client genAiClient = new Client()) {
            Povzetkar povzetkar = getPovzetkar(genAiClient, apiKey);
            povzetkar.run(runConfiguration.useCase(), runConfiguration.date());
        }
    }

    @NotNull
    private static Povzetkar getPovzetkar(Client genAiClient, String apiKey) {
        VideoSummary videoSummary = new VideoSummary(genAiClient);
        ProcessingStateService stateService = new ProcessingStateService();
        YouTubeDataService youtubeDataService = new YouTubeDataService(apiKey);
        VideoSummarizationService summarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary);
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
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d.M.yyyy");

        for (String arg : args) {
            if (arg.toLowerCase().startsWith("--use-case=")) {
                useCase = arg.substring("--use-case=".length()).trim();
            } else if (arg.toLowerCase().startsWith("--date=")) {
                String dateStr = arg.substring("--date=".length()).trim();
                date = LocalDate.parse(dateStr, formatter);
            }
        }
        RunConfiguration config = new RunConfiguration(useCase, date);
        logger.info("Running with configuration: {}", config);
        return config;
    }
}