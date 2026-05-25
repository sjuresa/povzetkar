package com.povzetkar.quarkus.service.transcript;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.thoroldvix.api.TranscriptApiFactory;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Manual comparison test — NOT run in CI.
 *
 * Run locally with:
 *   SUPADATA_API_KEY=sd_xxx mvn test -Dtest=TranscriptProviderComparisonTest -Dtranscript.compare.video=dQw4w9WgXcQ
 *
 * Requires a valid SUPADATA_API_KEY environment variable.
 * The video must have captions available on both providers.
 */
@Tag("manual")
class TranscriptProviderComparisonTest {

    private static final String VIDEO_ID = System.getProperty("transcript.compare.video", "dQw4w9WgXcQ");
    private static final String SUPADATA_API_KEY = System.getenv("SUPADATA_API_KEY");
    private static final String SUPADATA_URL = "https://api.supadata.ai";

    @Test
    void compareTranscriptProviders() {
        System.out.println("=== Transcript Provider Comparison ===");
        System.out.println("Video ID: " + VIDEO_ID);
        System.out.println();

        String youtubeLegacyResult = fetchYouTubeLegacy();
        String supadataResult = fetchSupadata();

        printResult("youtube-legacy", youtubeLegacyResult);
        printResult("supadata", supadataResult);

        System.out.println("=== Stats ===");
        System.out.printf("youtube-legacy: %d chars, %d lines%n",
                youtubeLegacyResult.length(), countLines(youtubeLegacyResult));
        System.out.printf("supadata:       %d chars, %d lines%n",
                supadataResult.length(), countLines(supadataResult));

        assertThat(youtubeLegacyResult)
                .as("youtube-legacy mora vrniti neprazen transkript")
                .isNotBlank()
                .doesNotStartWith("[youtube-legacy] FAILED");
    }

    private String fetchYouTubeLegacy() {
        System.out.println("[youtube-legacy] Fetching...");
        try {
            var provider = new YouTubeLegacyTranscriptProvider(
                    TranscriptApiFactory.createDefault(), true, 0, 0, new TimeTextFormatter());
            String result = provider.fetch(VIDEO_ID);
            System.out.println("[youtube-legacy] OK (" + result.length() + " chars)");
            return result;
        } catch (Exception e) {
            String msg = "[youtube-legacy] FAILED: " + e.getMessage();
            System.out.println(msg);
            return msg;
        }
    }

    private String fetchSupadata() {
        if (SUPADATA_API_KEY == null || SUPADATA_API_KEY.isBlank()) {
            String msg = "[supadata] SKIPPED — SUPADATA_API_KEY not set";
            System.out.println(msg);
            return msg;
        }
        System.out.println("[supadata] Fetching...");
        try {
            String youtubeUrl = "https://www.youtube.com/watch?v=" + VIDEO_ID;
            String encodedUrl = URLEncoder.encode(youtubeUrl, StandardCharsets.UTF_8);
            HttpClient http = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(SUPADATA_URL + "/v1/transcript?url=" + encodedUrl + "&lang=en&text=false"))
                    .header("x-api-key", SUPADATA_API_KEY)
                    .GET()
                    .build();
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println("[supadata] HTTP " + response.statusCode());

            if (response.statusCode() == 200) {
                ObjectMapper mapper = new ObjectMapper();
                SupadataTranscriptResponse body = mapper.readValue(response.body(), SupadataTranscriptResponse.class);
                List<TranscriptSegment> segments = body.content().stream()
                        .map(c -> new TranscriptSegment(c.offset() / 1000.0, c.text()))
                        .toList();
                String result = new TimeTextFormatter().format(segments);
                System.out.println("[supadata] OK (" + result.length() + " chars)");
                return result;
            } else {
                String msg = "[supadata] HTTP " + response.statusCode() + ": " + response.body();
                System.out.println(msg);
                return msg;
            }
        } catch (Exception e) {
            String msg = "[supadata] FAILED: " + e.getMessage();
            System.out.println(msg);
            return msg;
        }
    }

    private static void printResult(String provider, String result) {
        System.out.println();
        System.out.println("--- " + provider + " ---");
        String preview = result.length() > 800 ? result.substring(0, 800) + "\n[... truncated]" : result;
        System.out.println(preview);
    }

    private static int countLines(String s) {
        if (s == null || s.isEmpty()) return 0;
        return s.split("\n").length;
    }
}
