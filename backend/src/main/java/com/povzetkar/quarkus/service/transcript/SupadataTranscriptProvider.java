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

@ApplicationScoped
public class SupadataTranscriptProvider implements TranscriptProvider {

    private static final Logger log = LoggerFactory.getLogger(SupadataTranscriptProvider.class);
    private static final String PROVIDER_NAME = "supadata";
    private static final String YOUTUBE_URL_PREFIX = "https://www.youtube.com/watch?v=";
    private static final long POLL_INTERVAL_MS = 1000L;
    private static final long POLL_TIMEOUT_MS = 10_000L;

    private final SupadataClient supadataClient;
    private final TimeTextFormatter formatter;
    private final boolean enabled;
    private final String lang;

    @Inject
    public SupadataTranscriptProvider(
            @RestClient SupadataClient supadataClient,
            @ConfigProperty(name = "transcript.providers.supadata.enabled", defaultValue = "false") boolean enabled,
            @ConfigProperty(name = "transcript.providers.supadata.lang", defaultValue = "en") String lang) {
        this(supadataClient, enabled, lang, new TimeTextFormatter());
    }

    SupadataTranscriptProvider(SupadataClient supadataClient, boolean enabled, String lang, TimeTextFormatter formatter) {
        this.supadataClient = supadataClient;
        this.formatter = formatter;
        this.enabled = enabled;
        this.lang = lang;
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
    public String fetch(String videoId) throws QuotaExhaustedException, ProviderUnavailableException {
        String url = YOUTUBE_URL_PREFIX + videoId;
        String asyncJobId = null;
        try (Response response = supadataClient.getTranscript(url, lang, false)) {
            int status = response.getStatus();
            if (status == Response.Status.OK.getStatusCode()) {
                SupadataTranscriptResponse body = response.readEntity(SupadataTranscriptResponse.class);
                if (body == null) {
                    throw new ProviderUnavailableException(PROVIDER_NAME, "Empty response body on 200 OK");
                }
                return formatChunks(body.content());
            }
            if (status == Response.Status.ACCEPTED.getStatusCode()) {
                SupadataAsyncResponse async = response.readEntity(SupadataAsyncResponse.class);
                if (async == null || async.jobId() == null) {
                    throw new ProviderUnavailableException(PROVIDER_NAME, "202 response missing jobId");
                }
                asyncJobId = async.jobId();
            } else if (status == Response.Status.PAYMENT_REQUIRED.getStatusCode()) {
                log.warn("Supadata quota exhausted for video ID '{}'.", videoId);
                throw new QuotaExhaustedException(PROVIDER_NAME);
            } else {
                log.warn("Supadata returned HTTP {} for video ID '{}'.", status, videoId);
                throw new ProviderUnavailableException(PROVIDER_NAME, "HTTP " + status);
            }
        } catch (QuotaExhaustedException | ProviderUnavailableException e) {
            throw e;
        } catch (WebApplicationException e) {
            int status = e.getResponse().getStatus();
            log.warn("Supadata call failed with HTTP {} for video ID '{}'.", status, videoId);
            if (status == Response.Status.PAYMENT_REQUIRED.getStatusCode()) {
                throw new QuotaExhaustedException(PROVIDER_NAME);
            }
            throw new ProviderUnavailableException(PROVIDER_NAME, "HTTP " + status);
        } catch (Exception e) {
            log.warn("Supadata call failed for video ID '{}'.", videoId, e);
            throw new ProviderUnavailableException(PROVIDER_NAME, e);
        }
        // Response is closed; start polling outside the try-with-resources to release the connection
        return pollJobResult(videoId, asyncJobId);
    }

    private String pollJobResult(String videoId, String jobId) throws ProviderUnavailableException {
        long deadline = System.currentTimeMillis() + POLL_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            try {
                Thread.sleep(POLL_INTERVAL_MS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                throw new ProviderUnavailableException(PROVIDER_NAME, "Polling interrupted");
            }
            try {
                SupadataJobStatusResponse status = supadataClient.getJobStatus(jobId);
                if ("completed".equals(status.status())) {
                    return formatChunks(status.content());
                }
                if ("failed".equals(status.status())) {
                    log.warn("Supadata async job failed for video ID '{}': {}", videoId, status.error());
                    throw new ProviderUnavailableException(PROVIDER_NAME, "Async job failed: " + status.error());
                }
            } catch (WebApplicationException e) {
                log.warn("Supadata job status check failed for video ID '{}': HTTP {}", videoId, e.getResponse().getStatus());
                throw new ProviderUnavailableException(PROVIDER_NAME, "Job status check failed");
            }
        }
        log.warn("Supadata polling timed out after {}ms for video ID '{}'.", POLL_TIMEOUT_MS, videoId);
        throw new ProviderUnavailableException(PROVIDER_NAME, "Polling timeout after " + POLL_TIMEOUT_MS + "ms");
    }

    private String formatChunks(List<SupadataChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return "";
        }
        List<TranscriptSegment> segments = chunks.stream()
                .map(c -> new TranscriptSegment(c.offset() / 1000.0, Optional.ofNullable(c.text()).orElse("")))
                .toList();
        return formatter.format(segments);
    }
}
