package com.povzetkar.quarkus.service.transcript;

import io.github.thoroldvix.api.TranscriptRetrievalException;
import io.github.thoroldvix.api.YoutubeClient;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.random.RandomGenerator;

/**
 * The HTTP seam of youtube-transcript-api for youtube-legacy (POV-222), replacing the library's
 * {@code DefaultYoutubeClient} (final, package-private, no timeouts): same requests and error messages, plus
 * connect/request timeouts so a hung call cannot stall the serial fetch queue.
 * <p>
 * A paced instance belongs to one fetch (one thread): it counts its own calls and waits a random in-fetch
 * pause before every call except the first — like a person who opens the video, then the captions.
 */
final class YoutubeHttpClient implements YoutubeClient {

    static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(30);
    private static final String DEFAULT_ERROR_MESSAGE = "Request to YouTube failed.";

    private final HttpClient http;
    private final PauseRange inFetchPause;
    private final YouTubeRequestPacer.Sleeper sleeper;
    private final RandomGenerator random;
    private final Duration requestTimeout;
    private int calls;

    private YoutubeHttpClient(HttpClient http, PauseRange inFetchPause, YouTubeRequestPacer.Sleeper sleeper, RandomGenerator random,
                              Duration requestTimeout) {
        this.http = http;
        this.inFetchPause = inFetchPause;
        this.sleeper = sleeper;
        this.random = random;
        this.requestTimeout = requestTimeout;
    }

    /** The shared transport for all instances. */
    static HttpClient newHttpClient() {
        return HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
    }

    /** For one automatic fetch, run inside a {@link YouTubeRequestPacer} slot. */
    static YoutubeHttpClient paced(HttpClient http, PauseRange inFetchPause, YouTubeRequestPacer.Sleeper sleeper, RandomGenerator random) {
        return new YoutubeHttpClient(http, inFetchPause, sleeper, random, REQUEST_TIMEOUT);
    }

    /** For the manual transcript capture: no pauses (POV-222, exception to the pacing). */
    static YoutubeHttpClient unpaced(HttpClient http) {
        return new YoutubeHttpClient(http, null, null, null, REQUEST_TIMEOUT);
    }

    /** A copy with another request timeout (tests). */
    YoutubeHttpClient withRequestTimeout(Duration timeout) {
        return new YoutubeHttpClient(http, inFetchPause, sleeper, random, timeout);
    }

    boolean isPaced() {
        return inFetchPause != null;
    }

    @Override
    public String get(String url, Map<String, String> headers) throws TranscriptRetrievalException {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(url)).timeout(requestTimeout);
        headers.forEach(request::header);
        return send(request.build());
    }

    @Override
    public String post(String url, String json) throws TranscriptRetrievalException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(requestTimeout)
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
        return send(request);
    }

    private String send(HttpRequest request) throws TranscriptRetrievalException {
        pauseBeforeCall();
        HttpResponse<String> response;
        // The request timeout of HttpRequest covers only the response headers (JDK < 26); bounding the whole
        // exchange keeps a stalled body from holding the pacer slot for ever (POV-222).
        CompletableFuture<HttpResponse<String>> exchange = http.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        try {
            response = exchange.get(requestTimeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            exchange.cancel(true);
            throw new TranscriptRetrievalException(DEFAULT_ERROR_MESSAGE, e);
        } catch (ExecutionException e) {
            throw new TranscriptRetrievalException(DEFAULT_ERROR_MESSAGE, e.getCause());
        } catch (InterruptedException e) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            if (isPaced()) {
                // shutdown during an automatic fetch: not a failed attempt (POV-222)
                throw new TranscriptSkippedException("Interrupted while waiting for YouTube's response.");
            }
            throw new TranscriptRetrievalException(DEFAULT_ERROR_MESSAGE, e);
        }
        if (response.statusCode() != 200) {
            throw new TranscriptRetrievalException(DEFAULT_ERROR_MESSAGE + " Status code: " + response.statusCode());
        }
        return response.body();
    }

    private void pauseBeforeCall() {
        if (inFetchPause == null || calls++ == 0) {
            return;
        }
        try {
            sleeper.sleep(inFetchPause.draw(random));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            throw new TranscriptSkippedException("Interrupted during the pause inside a YouTube fetch.");
        }
    }
}
