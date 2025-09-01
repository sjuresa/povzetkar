package si.jumis;

import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.ai.GenAiPool;
import si.jumis.ai.VideoSummary;
import si.jumis.config.AppConfig;
import si.jumis.service.AuthorService;
import si.jumis.service.ContentCreationService;
import si.jumis.service.DefaultFileWriter;
import si.jumis.service.DocumentationService;
import si.jumis.service.FailureLoggerService;
import si.jumis.service.FileWriter;
import si.jumis.service.NewVideoFetcherService;
import si.jumis.service.ProcessingStateService;
import si.jumis.service.TimeTextFormatter;
import si.jumis.service.VideoSummarizationService;
import si.jumis.service.YouTubeDataService;
import si.jumis.service.YouTubeTranscriptService;
import si.jumis.util.ResourceReader;
import si.jumis.util.Sleeper;

import java.time.Clock;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class Main {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private static final long COOLDOWN_PERIOD_NS = TimeUnit.SECONDS.toNanos(60);

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

        AppConfig appConfig = new AppConfig("povzetkar.properties");
        RunConfiguration runConfiguration = appConfig.getRunConfiguration();
        logger.info("Running with configuration: {}", runConfiguration);

        try (GenAiPool genAiPool = new GenAiPool(googleApiKeys, COOLDOWN_PERIOD_NS, new Sleeper.DefaultSleeper(), new GenAiPool.DefaultTimeProvider())) {
            Povzetkar povzetkar = getPovzetkar(genAiPool, youtubeApiKey, ZoneId.of(timezoneId), appConfig);
            povzetkar.run(runConfiguration);
        }
    }

    @NotNull
    private static Povzetkar getPovzetkar(GenAiPool genAiPool, String apiKey, ZoneId zoneId, AppConfig appConfig) {
        VideoSummary videoSummary = new VideoSummary(genAiPool);
        ProcessingStateService stateService = new ProcessingStateService();
        YouTubeDataService youtubeDataService = new YouTubeDataService(apiKey,
                new YouTube.Builder(new NetHttpTransport(), new GsonFactory(), null)
                        .setApplicationName(appConfig.getYoutubeAppName()).build(), zoneId);
        YouTubeTranscriptService transcriptService = new YouTubeTranscriptService(new TimeTextFormatter());
        FileWriter fileWriter = new DefaultFileWriter();
        ResourceReader resourceReader = new ResourceReader();
        NewVideoFetcherService newVideoFetcherService = new NewVideoFetcherService(youtubeDataService, stateService);
        ContentCreationService contentCreationService = new ContentCreationService(transcriptService);
        FailureLoggerService failureLoggerService = new FailureLoggerService(fileWriter, Clock.system(zoneId));
        VideoSummarizationService summarizationService = new VideoSummarizationService(stateService,
                videoSummary, fileWriter, newVideoFetcherService, contentCreationService, failureLoggerService);
        AuthorService authorService = new AuthorService(youtubeDataService, appConfig);
        DocumentationService documentationService = new DocumentationService(Clock.system(zoneId), appConfig);

        return new Povzetkar(summarizationService, videoSummary, resourceReader, authorService, documentationService, appConfig);
    }
}
