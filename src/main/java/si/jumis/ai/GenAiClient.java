package si.jumis.ai;

import com.google.genai.Client;
import com.google.genai.Models;

import java.util.concurrent.TimeUnit;

public class GenAiClient {

    final Client client;
    volatile long lastUsedTimestamp; // in nanoseconds

    GenAiClient(Client client) {
        this.client = client;
        // Initialize to a time in the past to allow immediate use
        this.lastUsedTimestamp = System.nanoTime() - TimeUnit.SECONDS.toNanos(60);
    }

    public void usedNow() {
        this.lastUsedTimestamp = System.nanoTime();
    }

    public Models getModels() {
        return client.models;
    }
}
