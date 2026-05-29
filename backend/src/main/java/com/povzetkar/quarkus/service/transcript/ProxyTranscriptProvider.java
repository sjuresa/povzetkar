package com.povzetkar.quarkus.service.transcript;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
public class ProxyTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(ProxyTranscriptProvider.class);
    private static final String PROVIDER_NAME = "proxy";

    private final TranscriptProxyClient proxyClient;
    private final TimeTextFormatter formatter;
    private final boolean enabled;
    private final String apiKey;
    private final int jitterMinMs;
    private final int jitterMaxMs;

    // Tracks the last fetch time so jitter is applied only between sequential calls (batch),
    // not for the first or an isolated call (e.g. the manual admin "Fetch Transcript" button).
    private final AtomicLong lastInvocationMs = new AtomicLong(0L);

    @Inject
    public ProxyTranscriptProvider(
            @RestClient TranscriptProxyClient proxyClient,
            @ConfigProperty(name = "transcript.providers.proxy.enabled", defaultValue = "true") boolean enabled,
            @ConfigProperty(name = "transcript.providers.proxy.api-key", defaultValue = "") String apiKey,
            @ConfigProperty(name = "transcript.providers.proxy.jitter.min-ms", defaultValue = "15000") int jitterMinMs,
            @ConfigProperty(name = "transcript.providers.proxy.jitter.max-ms", defaultValue = "45000") int jitterMaxMs) {
        this(proxyClient, enabled, apiKey, jitterMinMs, jitterMaxMs, new TimeTextFormatter());
    }

    ProxyTranscriptProvider(TranscriptProxyClient proxyClient, boolean enabled, String apiKey, int jitterMinMs, int jitterMaxMs, TimeTextFormatter formatter) {
        this.proxyClient = proxyClient;
        this.formatter = formatter;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.jitterMinMs = jitterMinMs;
        this.jitterMaxMs = jitterMaxMs;
    }

    @Override
    public String name() {
        return PROVIDER_NAME;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public String fetch(String videoId) throws ProviderUnavailableException {
        applyJitter();

        try (Response response = proxyClient.getTranscript(videoId, apiKey)) {
            int status = response.getStatus();
            if (status == Response.Status.OK.getStatusCode()) {
                ProxyTranscriptResponse body = response.readEntity(ProxyTranscriptResponse.class);
                if (body == null || body.segments() == null) {
                    throw new ProviderUnavailableException(PROVIDER_NAME, "Malformed 200 OK response: missing body or segments");
                }
                return formatSegments(body.segments());
            }
            String detail = readBodySafely(response);
            log.warn("Proxy returned HTTP {} for video ID '{}': {}", status, videoId, detail);
            throw new ProviderUnavailableException(PROVIDER_NAME, "HTTP " + status);
        } catch (ProviderUnavailableException e) {
            throw e;
        } catch (WebApplicationException e) {
            // Quarkus REST Client throws WebApplicationException for non-2xx by default,
            // so read the status + body (detail.reason) here for observability.
            int status;
            String detail;
            try (Response r = e.getResponse()) {
                status = (r != null) ? r.getStatus() : -1;
                detail = (r != null) ? readBodySafely(r) : "<no response>";
            }
            log.warn("Proxy returned HTTP {} for video ID '{}': {}", status, videoId, detail);
            throw new ProviderUnavailableException(PROVIDER_NAME, "HTTP " + status);
        } catch (Exception e) {
            log.warn("Proxy call failed for video ID '{}'.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        }
    }

    private String formatSegments(List<ProxySegment> segments) {
        if (segments == null || segments.isEmpty()) {
            return "";
        }
        List<TranscriptSegment> mapped = segments.stream()
                .map(s -> new TranscriptSegment(s.start(), Optional.ofNullable(s.text()).orElse("")))
                .toList();
        return formatter.format(mapped);
    }

    private String readBodySafely(Response response) {
        try {
            String body = response.readEntity(String.class);
            return (body != null) ? body : "<no body>";
        } catch (Exception _) {
            return "<unreadable body>";
        }
    }

    private void applyJitter() {
        if (jitterMinMs <= 0 && jitterMaxMs <= 0) {
            return;
        }
        long now = System.currentTimeMillis();
        long last = lastInvocationMs.getAndSet(now);
        boolean inSequence = (last != 0L) && (now - last < jitterMaxMs);
        if (!inSequence) {
            return;
        }
        int range = Math.max(1, jitterMaxMs - jitterMinMs);
        int delay = jitterMinMs + ThreadLocalRandom.current().nextInt(range);
        if (delay <= 0) {
            return;
        }
        try {
            Thread.sleep(delay);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }
}
