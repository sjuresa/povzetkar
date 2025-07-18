package si.jumis;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.VideoSummary;
import si.jumis.ai.YouTubeDataService;
import si.jumis.service.ProcessingStateService;
import si.jumis.service.VideoSummarizationService;
import si.jumis.util.ResourceReader;

public class Main {

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    /**
     * Holds the application's runtime configuration, parsed from command-line arguments.
     */
    private record RunConfiguration(String useCase, boolean dryRun) {}

    public static void main(String[] args) {
        RunConfiguration config = parseRunConfiguration(args);

        // --- Dependency Injection Setup (Composition Root) ---
        Client genAiClient = new Client();
        String apiKey = System.getenv("YOUTUBE_API_KEY");

        VideoSummary videoSummary = new VideoSummary(genAiClient);
        ProcessingStateService stateService = new ProcessingStateService();
        YouTubeDataService youtubeDataService = new YouTubeDataService(apiKey);
        VideoSummarizationService summarizationService = new VideoSummarizationService(youtubeDataService, stateService, videoSummary);
        ResourceReader resourceReader = new ResourceReader();

        // --- Application Runner ---
        Povzetkar povzetkar = new Povzetkar(summarizationService, videoSummary, resourceReader);
        povzetkar.run(config.useCase(), config.dryRun());
    }

    /**
     * Parses command-line arguments to determine the application's run configuration.
     * Defaults to useCase="S_B" and dryRun=false if no arguments are provided.
     *
     * @param args Command-line arguments. Supports --use-case=[S_B|TR|S_URL] and --dry-run.
     * @return A {@link RunConfiguration} object.
     */
    private static RunConfiguration parseRunConfiguration(String[] args) {
        String useCase = "S_B"; // Default use case
        boolean dryRun = false;   // Default dry run mode

        for (String arg : args) {
            if (arg.equalsIgnoreCase("--dry-run")) {
                dryRun = true;
            } else if (arg.toLowerCase().startsWith("--use-case=")) {
                useCase = arg.substring(11).trim();
            }
        }

        logger.info("Running with configuration: useCase='{}', dryRun={}", useCase, dryRun);
        return new RunConfiguration(useCase, dryRun);
    }
}