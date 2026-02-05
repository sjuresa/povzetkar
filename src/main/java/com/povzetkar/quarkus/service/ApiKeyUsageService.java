package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class ApiKeyUsageService {

    private static final Logger logger = LoggerFactory.getLogger(ApiKeyUsageService.class);
    private final Clock clock;
    private final ZoneId zoneId;
    private final EntityManager entityManager;

    @Inject
    public ApiKeyUsageService(
            @ConfigProperty(name = "povzetkar.api-usage.timezone", defaultValue = "America/Los_Angeles") String timezoneId,
            EntityManager entityManager) {
        this.zoneId = ZoneId.of(timezoneId);
        this.clock = Clock.system(this.zoneId);
        this.entityManager = entityManager;
    }

    // Constructor for testing purposes
    public ApiKeyUsageService(Clock clock, EntityManager entityManager) {
        this.clock = clock;
        this.zoneId = clock.getZone();
        this.entityManager = entityManager;
    }

    public void incrementUsage(String apiKeyName) {
        try {
            incrementUsageTransactional(apiKeyName);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("Race condition detected when creating ApiKeyUsage for {}. Retrying operation.", apiKeyName);
                // Retry the operation. This time, the record will exist.
                incrementUsageTransactional(apiKeyName);
            } else {
                throw e;
            }
        }
    }

    @Transactional
    void incrementUsageTransactional(String apiKeyName) {
        LocalDate today = LocalDate.now(clock);

        int updatedCount = entityManager.createQuery(
                        "UPDATE ApiKeyUsage SET usageCount = usageCount + 1 WHERE apiKeyName = :apiKeyName AND usageDate = :usageDate")
                .setParameter("apiKeyName", apiKeyName)
                .setParameter("usageDate", today)
                .executeUpdate();

        if (updatedCount == 0) {
            // The record did not exist, so we create it.
            // This is the point where a race condition can occur.
            ApiKeyUsage newUsage = new ApiKeyUsage(apiKeyName, today);
            entityManager.persist(newUsage);
        }
        entityManager.flush();
    }

    public void incrementErrorCount(String apiKeyName) {
        try {
            incrementErrorCountTransactional(apiKeyName);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("Race condition detected when creating ApiKeyUsage for {}. Retrying operation.", apiKeyName);
                // Retry the operation. This time, the record will exist.
                incrementErrorCountTransactional(apiKeyName);
            } else {
                throw e;
            }
        }
    }

    @Transactional
    void incrementErrorCountTransactional(String apiKeyName) {
        LocalDate today = LocalDate.now(clock);

        int updatedCount = entityManager.createQuery(
                        "UPDATE ApiKeyUsage SET errorCount = errorCount + 1 WHERE apiKeyName = :apiKeyName AND usageDate = :usageDate")
                .setParameter("apiKeyName", apiKeyName)
                .setParameter("usageDate", today)
                .executeUpdate();

        if (updatedCount == 0) {
            // The record did not exist, so we create it.
            // usageCount = 0, errorCount = 1
            ApiKeyUsage newUsage = new ApiKeyUsage(apiKeyName, today, 0, 1);
            entityManager.persist(newUsage);
        }
        entityManager.flush();
    }

    public List<ApiKeyUsage> getUsageForToday() {
        LocalDate today = LocalDate.now(clock);
        return ApiKeyUsage.list("usageDate = ?1 order by apiKeyName", today);
    }

    public String getTimezoneId() {
        return zoneId.getId();
    }
}
