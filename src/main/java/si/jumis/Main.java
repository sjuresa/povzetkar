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

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Holds the application's runtime configuration, parsed from command-line arguments.
     */
    private record RunConfiguration(String useCase) {
    }

    public static void main(String[] args) {
        RunConfiguration config = parseRunConfiguration(args);

        String apiKey = System.getenv("YOUTUBE_API_KEY");
        if (apiKey == null || apiKey.isBlank()) {
            logger.error("YOUTUBE_API_KEY environment variable not set. Exiting.");
            return;
        }
        try (Client genAiClient = new Client()) {
            Povzetkar povzetkar = getPovzetkar(genAiClient, apiKey);
            povzetkar.run(config.useCase(), LocalDate.of(2025, 7, 19));
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
     * Defaults to useCase="S_B" if no arguments are provided.
     *
     * @param args Command-line arguments. Supports --use-case=[S_B|TR|S_URL].
     * @return A {@link RunConfiguration} object.
     */
    private static RunConfiguration parseRunConfiguration(String[] args) {
        String useCase = "S_B"; // Default use case

        for (String arg : args) {
            if (arg.toLowerCase().startsWith("--use-case=")) {
                useCase = arg.substring("--use-case=".length()).trim();
            }
        }

        logger.info("Running with configuration: useCase='{}'", useCase);
        return new RunConfiguration(useCase);
    }
}