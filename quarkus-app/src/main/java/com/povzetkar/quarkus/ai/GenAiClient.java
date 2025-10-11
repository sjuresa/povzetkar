package com.povzetkar.quarkus.ai;

import com.google.genai.Client;
import com.google.genai.Models;

import java.util.concurrent.TimeUnit;

public class GenAiClient {

    final Client client;
    final String name;
    volatile long lastUsedTimestamp; // in nanoseconds
    private final GenAiPool.TimeProvider timeProvider;

    GenAiClient(Client client, String name, GenAiPool.TimeProvider timeProvider) {
        this.client = client;
        this.name = name;
        this.timeProvider = timeProvider;
        // Initialize to a time in the past to allow immediate use
        this.lastUsedTimestamp = timeProvider.nanoTime() - TimeUnit.SECONDS.toNanos(60);
    }

    public void usedNow() {
        this.lastUsedTimestamp = timeProvider.nanoTime();
    }

    public Models getModels() {
        return client.models;
    }
}
