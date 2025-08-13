package si.jumis.ai;

import com.google.genai.Client;
import com.google.genai.Models;

import java.util.concurrent.TimeUnit;

public class GenAiClient {

    final Client client;
    final String name;
    volatile long lastUsedTimestamp; // in nanoseconds

    GenAiClient(Client client, String name) {
        this.client = client;
        this.name = name;
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
