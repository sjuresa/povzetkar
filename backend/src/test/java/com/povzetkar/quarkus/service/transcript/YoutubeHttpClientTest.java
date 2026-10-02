package com.povzetkar.quarkus.service.transcript;

import com.sun.net.httpserver.HttpServer;
import io.github.thoroldvix.api.TranscriptRetrievalException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.random.RandomGenerator;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class YoutubeHttpClientTest {

    private static final PauseRange IN_FETCH = new PauseRange(Duration.ofSeconds(1), Duration.ofSeconds(5));

    private final HttpClient http = YoutubeHttpClient.newHttpClient();
    private final List<Duration> sleeps = new CopyOnWriteArrayList<>();
    private final List<String> received = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private String baseUrl;
    private volatile int status = 200;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            received.add(exchange.getRequestMethod() + " " + exchange.getRequestURI()
                    + " lang=" + exchange.getRequestHeaders().getFirst("Accept-Language")
                    + " body=" + body);
            byte[] response = ("reply to " + exchange.getRequestURI().getPath()).getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(response);
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    @DisplayName("GET sends the given headers and returns the body")
    void getSendsHeadersAndReturnsBody() throws Exception {
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http);

        String body = client.get(baseUrl + "/watch?v=abc", Map.of("Accept-Language", "en-US"));

        assertThat(body).isEqualTo("reply to /watch");
        assertThat(received).containsExactly("GET /watch?v=abc lang=en-US body=");
    }

    @Test
    @DisplayName("POST sends the JSON body and returns the response body")
    void postSendsBody() throws Exception {
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http);

        String body = client.post(baseUrl + "/youtubei/v1/player?key=k", "{\"videoId\":\"abc\"}");

        assertThat(body).isEqualTo("reply to /youtubei/v1/player");
        assertThat(received).containsExactly("POST /youtubei/v1/player?key=k lang=null body={\"videoId\":\"abc\"}");
    }

    @Test
    @DisplayName("a status other than 200 fails with the library's message and the status code")
    void non200FailsWithStatusCode() {
        status = 429;
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http);

        assertThatThrownBy(() -> client.get(baseUrl + "/api/timedtext", Map.of()))
                .isInstanceOf(TranscriptRetrievalException.class)
                .hasMessage("Request to YouTube failed. Status code: 429");
    }

    @Test
    @DisplayName("an I/O failure fails with the library's message")
    void ioFailureFailsWithLibraryMessage() throws Exception {
        int closedPort;
        try (ServerSocket socket = new ServerSocket(0, 0, InetAddress.getLoopbackAddress())) {
            closedPort = socket.getLocalPort();
        }
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http);

        assertThatThrownBy(() -> client.get("http://127.0.0.1:" + closedPort + "/watch?v=abc", Map.of()))
                .isInstanceOf(TranscriptRetrievalException.class)
                .hasMessage("Request to YouTube failed.")
                .hasCauseInstanceOf(IOException.class);
    }

    @Test
    @DisplayName("paced: no pause before the first call, a drawn in-fetch pause before each further call")
    void pacedPausesBeforeEveryCallButTheFirst() throws Exception {
        YoutubeHttpClient client = YoutubeHttpClient.paced(http, IN_FETCH, sleeps::add, fixed(2_500));

        client.get(baseUrl + "/watch?v=abc", Map.of());
        assertThat(sleeps).isEmpty();
        client.post(baseUrl + "/youtubei/v1/player?key=k", "{}");
        client.get(baseUrl + "/api/timedtext", Map.of());

        assertThat(sleeps).containsExactly(Duration.ofMillis(2_500), Duration.ofMillis(2_500));
        assertThat(received).hasSize(3);
    }

    @Test
    @DisplayName("unpaced (manual capture): never pauses")
    void unpacedNeverPauses() throws Exception {
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http);

        client.get(baseUrl + "/watch?v=abc", Map.of());
        client.post(baseUrl + "/youtubei/v1/player?key=k", "{}");
        client.get(baseUrl + "/api/timedtext", Map.of());

        assertThat(sleeps).isEmpty();
        assertThat(received).hasSize(3);
    }

    @Test
    @DisplayName("interrupted in-fetch pause: skipped, the call is not sent, interrupt flag kept")
    void interruptedInFetchPauseSkips() throws Exception {
        YoutubeHttpClient client = YoutubeHttpClient.paced(http, IN_FETCH, _ -> {
            throw new InterruptedException("shutdown");
        }, fixed(2_500));
        client.get(baseUrl + "/watch?v=abc", Map.of());

        try {
            assertThatThrownBy(() -> client.post(baseUrl + "/youtubei/v1/player?key=k", "{}"))
                    .isInstanceOf(TranscriptSkippedException.class);
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
        } finally {
            Thread.interrupted(); // clear the flag for the following tests
        }
        assertThat(received).hasSize(1);
    }

    @Test
    @DisplayName("a response whose body stalls after the headers ends with the request timeout, not never")
    void stalledBodyTimesOut() throws Exception {
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/stall", exchange -> {
            exchange.sendResponseHeaders(200, 0); // chunked: headers sent, body follows
            exchange.getResponseBody().write("partial".getBytes(StandardCharsets.UTF_8));
            exchange.getResponseBody().flush();
            try {
                release.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            exchange.close();
        });
        YoutubeHttpClient client = YoutubeHttpClient.unpaced(http).withRequestTimeout(Duration.ofMillis(500));
        try {
            long start = System.nanoTime();
            assertThatThrownBy(() -> client.get(baseUrl + "/stall", Map.of()))
                    .isInstanceOf(TranscriptRetrievalException.class)
                    .hasMessage("Request to YouTube failed.");
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofSeconds(5));
        } finally {
            release.countDown();
        }
    }

    @Test
    @DisplayName("paced: interrupted while waiting for the response (shutdown) → skipped, not a failed attempt")
    void pacedInterruptedDuringSendSkips() throws Exception {
        CountDownLatch requestArrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/slow", exchange -> {
            requestArrived.countDown();
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
            exchange.sendResponseHeaders(200, -1);
            exchange.close();
        });
        YoutubeHttpClient client = YoutubeHttpClient.paced(http, IN_FETCH, sleeps::add, fixed(2_500));
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                client.get(baseUrl + "/slow", Map.of());
            } catch (Throwable t) {
                thrown.set(t);
            }
        });
        caller.start();
        try {
            assertThat(requestArrived.await(5, TimeUnit.SECONDS)).isTrue();
            caller.interrupt();
            caller.join(5_000);
        } finally {
            release.countDown();
        }

        assertThat(thrown.get()).isInstanceOf(TranscriptSkippedException.class);
    }

    private static RandomGenerator fixed(long millis) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                throw new UnsupportedOperationException("only bounded draws expected");
            }

            @Override
            public long nextLong(long origin, long bound) {
                assertThat(millis).isGreaterThanOrEqualTo(origin).isLessThan(bound);
                return millis;
            }
        };
    }
}
