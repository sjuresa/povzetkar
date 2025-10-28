package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.OrchestrationLock;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

@ApplicationScoped
public class OrchestrationLockService {

    private static final Logger logger = LoggerFactory.getLogger(OrchestrationLockService.class);
    private static final Duration LOCK_TIMEOUT = Duration.ofMinutes(10); // Adjust as needed

    public void acquireLock(LocalDate orchestrationDate) {
        QuarkusTransaction.requiringNew().run(() -> {
            Optional<OrchestrationLock> existingLock = OrchestrationLock.find("orchestrationDate", orchestrationDate)
                    .withLock(LockModeType.PESSIMISTIC_WRITE)
                    .firstResultOptional();

            if (existingLock.isPresent()) {
                OrchestrationLock lock = existingLock.get();
                Duration timeSinceLock = Duration.between(lock.lockedAt, Instant.now());
                if (timeSinceLock.compareTo(LOCK_TIMEOUT) < 0) {
                    throw new OrchestrationLockException("Orchestration already in progress for date: " + orchestrationDate);
                }
                // If the lock is old, we can assume the previous process crashed. We will update the lock.
                logger.warn("Orchestration lock found for date {} but it is older than {}. Assuming previous run crashed and taking over the lock.", orchestrationDate, LOCK_TIMEOUT);
                lock.lockedAt = Instant.now();
                lock.persist();
            } else {
                OrchestrationLock lock = new OrchestrationLock(orchestrationDate, Instant.now());
                lock.persist();
            }
        });
        logger.debug("Acquired orchestration lock for date: {}", orchestrationDate);
    }

    public void releaseLock(LocalDate orchestrationDate) {
        QuarkusTransaction.requiringNew().run(() -> {
            Optional<OrchestrationLock> existingLock = OrchestrationLock.find("orchestrationDate", orchestrationDate).firstResultOptional();
            existingLock.ifPresent(OrchestrationLock::delete);
        });
        logger.debug("Released orchestration lock for date: {}", orchestrationDate);
    }

    public static class OrchestrationLockException extends RuntimeException {
        public OrchestrationLockException(String message) {
            super(message);
        }
    }
}
