package com.povzetkar.quarkus.ai;

import com.google.api.client.util.Sleeper;
import com.google.genai.Client;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

@ApplicationScoped
public class GenAiPool implements AutoCloseable {
    public interface TimeProvider {
        long nanoTime();
    }

    public static class DefaultTimeProvider implements TimeProvider {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }

    private static final Logger logger = LoggerFactory.getLogger(GenAiPool.class);
    private final Sleeper sleeper;
    private final List<GenAiClient> clientPool;
    private final long coolDownPeriod;
    private final TimeProvider timeProvider;

    @Inject
    public GenAiPool(@ConfigProperty(name = "google.api.keys") List<String> apiKeys,
                     @ConfigProperty(name = "cooldown.period.sec") long coolDownPeriod) {
        this(apiKeys, coolDownPeriod, new DefaultTimeProvider(), Sleeper.DEFAULT);
    }

    GenAiPool(List<String> apiKeys, long coolDownPeriod, TimeProvider timeProvider, Sleeper sleeper) {
        this.coolDownPeriod = TimeUnit.SECONDS.toNanos(coolDownPeriod);
        this.timeProvider = timeProvider;
        this.sleeper = sleeper;
        clientPool = new ArrayList<>();

        for (int i = 0; i < apiKeys.size(); i++) {
            String apiKey = apiKeys.get(i).trim();
            if (!apiKey.isEmpty()) {
                Client client = Client.builder().apiKey(apiKey).build();
                clientPool.add(new GenAiClient(client, "API key " + (i + 1), timeProvider));
            }
        }
    }

    public GenAiClient getGenAiClient() {
        GenAiClient selectedClient;
        long timeSinceLastUse;
        long actualAcquisitionTime;

        synchronized (this) {
            selectedClient = selectClient();
            long now = timeProvider.nanoTime();
            timeSinceLastUse = now - selectedClient.lastUsedTimestamp;

            if (timeSinceLastUse < coolDownPeriod) {
                long waitTimeNs = coolDownPeriod - timeSinceLastUse;
                long millis = TimeUnit.NANOSECONDS.toMillis(waitTimeNs);
                logger.info("Waiting for {} seconds.", millis / 1000.0);
                try {
                    sleeper.sleep(millis);
                } catch (InterruptedException e) {
                    logger.warn("Sleep timer was interrupted.", e);
                    Thread.currentThread().interrupt();
                }
                actualAcquisitionTime = timeProvider.nanoTime(); // Time after sleep
            } else {
                actualAcquisitionTime = now; // No sleep needed, acquire now
            }
            selectedClient.lastUsedTimestamp = actualAcquisitionTime; // Update timestamp after cooldown
            logger.info("Using {}. Time since last use: {} s.", selectedClient.name, TimeUnit.NANOSECONDS.toSeconds(timeSinceLastUse));
        }
        return selectedClient;
    }

    private GenAiClient selectClient() {
        return clientPool.stream()
                .min(Comparator.comparingLong(c -> c.lastUsedTimestamp))
                .orElseThrow(() -> new NoSuchElementException("Client pool is empty"));
    }

    @Override
    public void close() {
        for (GenAiClient genAiClient : clientPool) {
            try {
                genAiClient.client.close();
            } catch (Exception e) {
                logger.error("Failed to close GenAiClient: {}", genAiClient.name, e);
            }
        }
    }
}
