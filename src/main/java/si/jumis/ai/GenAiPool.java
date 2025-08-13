package si.jumis.ai;

import com.google.genai.Client;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import si.jumis.util.Sleeper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

public class GenAiPool {
    private final List<GenAiClient> clientPool;
    private final Sleeper sleeper; // Add Sleeper dependency
    private final long coolDownPeriod;
    private static final Logger logger = LoggerFactory.getLogger(GenAiPool.class);

    public GenAiPool(String apiKey1, String apiKey2, long coolDownPeriod) {
        this.sleeper = new Sleeper.DefaultSleeper();
        this.coolDownPeriod = coolDownPeriod;

        clientPool = new ArrayList<>();

        if (apiKey1 != null && !apiKey1.isEmpty()) {
            Client client1 = Client.builder().apiKey(apiKey1).build();
            clientPool.add(new GenAiClient(client1, "Api key 1"));
        }
        if (apiKey2 != null && !apiKey2.isEmpty()) {
            Client client2 = Client.builder().apiKey(apiKey2).build();
            clientPool.add(new GenAiClient(client2, "API key 2"));
        }
    }
    /**
     * Selects a client from the pool that has not been used for at least 60 seconds.
     * If all clients are busy, it waits until one becomes available.
     *
     * @return A Models object from an available client.
     */
    public GenAiClient getGenAiClient() {
        GenAiClient selectedClient = selectClient();

        long now = System.nanoTime();
        long timeSinceLastUse = now - selectedClient.lastUsedTimestamp;
        logger.info("Using {}. Time since last use: {} s.", selectedClient.name, TimeUnit.NANOSECONDS.toSeconds(timeSinceLastUse));

        if (timeSinceLastUse < coolDownPeriod) {
            long waitTimeNs = coolDownPeriod - timeSinceLastUse;
            try {
                sleeper.sleep(TimeUnit.NANOSECONDS.toMillis(waitTimeNs));
            } catch (InterruptedException e) {
                logger.warn("Sleep timer was interrupted.", e);
                Thread.currentThread().interrupt();
            }
        }

        selectedClient.lastUsedTimestamp = System.nanoTime();
        return selectedClient;
    }

    /**
     * Finds the least recently used client from the pool.
     * This method is synchronized to ensure thread-safe access to the client pool.
     *
     * @return The least recently used PooledClient.
     */
    private synchronized GenAiClient selectClient() {
        // Find the least recently used client
        return clientPool.stream()
                .min(Comparator.comparingLong(c -> c.lastUsedTimestamp))
                .orElseThrow(() -> new NoSuchElementException("Client pool is empty"));
    }
}
