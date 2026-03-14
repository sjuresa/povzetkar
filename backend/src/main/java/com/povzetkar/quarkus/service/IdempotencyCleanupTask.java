package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.IdempotentRequest;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;

import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class IdempotencyCleanupTask {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyCleanupTask.class);

    @ConfigProperty(name = "povzetkar.idempotency.cleanup.older-than", defaultValue = "PT24H")
    Duration cleanupOlderThan;

    @Scheduled(every = "24h")
    @Transactional
    public void cleanupOldIdempotencyRequests() {
        log.info("Starting cleanup of old idempotency requests");
        Instant threshold = Instant.now().minus(cleanupOlderThan);
        long deletedCount = IdempotentRequest.delete("createdAt < ?1", threshold);
        log.info("Cleaned up {} old idempotency requests", deletedCount);
    }
}
