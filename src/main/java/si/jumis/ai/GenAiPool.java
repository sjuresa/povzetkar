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

    public interface TimeProvider {
        long nanoTime();
    }

    public static class DefaultTimeProvider implements TimeProvider {
        @Override
        public long nanoTime() {
            return System.nanoTime();
        }
    }

    private final List<GenAiClient> clientPool;
    private final Sleeper sleeper;
    private final long coolDownPeriod;
    private final TimeProvider timeProvider;
    private static final Logger logger = LoggerFactory.getLogger(GenAiPool.class);

    public GenAiPool(List<String> apiKeys, long coolDownPeriod, Sleeper sleeper, TimeProvider timeProvider) {
        this.sleeper = sleeper;
        this.coolDownPeriod = coolDownPeriod;
        this.timeProvider = timeProvider;

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

        synchronized (this) {
            selectedClient = selectClient();
            long now = timeProvider.nanoTime();
            timeSinceLastUse = now - selectedClient.lastUsedTimestamp;
            selectedClient.lastUsedTimestamp = now;
        }

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
        return selectedClient;
    }

    private GenAiClient selectClient() {
        return clientPool.stream()
                .min(Comparator.comparingLong(c -> c.lastUsedTimestamp))
                .orElseThrow(() -> new NoSuchElementException("Client pool is empty"));
    }
}
