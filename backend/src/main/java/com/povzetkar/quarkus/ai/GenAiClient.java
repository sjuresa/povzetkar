package com.povzetkar.quarkus.ai;

import com.google.genai.Client;
import com.google.genai.Models;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class GenAiClient {

    final Client client;
    final String name;
    volatile long lastUsedTimestamp; // in nanoseconds
    private final AtomicInteger consecutiveFailureCount = new AtomicInteger(0);

    GenAiClient(Client client, String name, GenAiPool.TimeProvider timeProvider) {
        this.client = client;
        this.name = name;
        // Initialize to a time in the past to allow immediate use
        this.lastUsedTimestamp = timeProvider.nanoTime() - TimeUnit.SECONDS.toNanos(60);
    }

    public Models getModels() {
        return client.models;
    }

    public String getName() {
        return name;
    }

    public int incrementAndGetFailures() {
        return consecutiveFailureCount.incrementAndGet();
    }

    public void resetFailures() {
        consecutiveFailureCount.set(0);
    }

    public int getFailures() {
        return consecutiveFailureCount.get();
    }
}
