package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceException;
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

    private enum CounterType {
        USAGE, ERROR
    }

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
        updateOrCreateUsage(apiKeyName, CounterType.USAGE);
    }

    public void incrementErrorCount(String apiKeyName) {
        updateOrCreateUsage(apiKeyName, CounterType.ERROR);
    }

    private void updateOrCreateUsage(String apiKeyName, CounterType counterType) {
        try {
            updateOrCreateUsageTransactional(apiKeyName, counterType);
        } catch (PersistenceException e) {
            if (e.getCause() instanceof ConstraintViolationException) {
                logger.warn("Race condition detected when creating ApiKeyUsage for {}. Retrying operation.", apiKeyName);
                // Retry the operation. This time, the record will exist.
                updateOrCreateUsageTransactional(apiKeyName, counterType);
            } else {
                throw e;
            }
        }
    }

    // Programmatic REQUIRES_NEW (not the @Transactional annotation) because this method is
    // reached only via same-class self-invocation from updateOrCreateUsage, which bypasses
    // the CDI interceptor — an annotation would be silently ignored. Committing in its own
    // transaction keeps the usage/error count independent of any caller transaction: a GenAI
    // API failure rolling back the caller must not roll back the RPD count of the request
    // that was already sent. Mirrors the AuditLogFilter best-effort-audit pattern.
    void updateOrCreateUsageTransactional(String apiKeyName, CounterType counterType) {
        QuarkusTransaction.requiringNew().run(() -> {
            LocalDate today = LocalDate.now(clock);
            String columnToIncrement = (counterType == CounterType.USAGE) ? "usageCount" : "errorCount";
            String updateQuery = "UPDATE ApiKeyUsage SET " + columnToIncrement + " = " + columnToIncrement + " + 1 WHERE apiKeyName = :apiKeyName AND usageDate = :usageDate";

            long initialUsage = (counterType == CounterType.USAGE) ? 1 : 0;
            long initialError = (counterType == CounterType.ERROR) ? 1 : 0;

            int updatedCount = entityManager.createQuery(updateQuery)
                    .setParameter("apiKeyName", apiKeyName)
                    .setParameter("usageDate", today)
                    .executeUpdate();

            if (updatedCount == 0) {
                ApiKeyUsage newUsage = new ApiKeyUsage(apiKeyName, today, initialUsage, initialError);
                entityManager.persist(newUsage);
            }
            entityManager.flush();
        });
    }

    public List<ApiKeyUsage> getUsageForToday() {
        LocalDate today = LocalDate.now(clock);
        return ApiKeyUsage.list("usageDate = ?1 order by apiKeyName", today);
    }

    public String getTimezoneId() {
        return zoneId.getId();
    }
}
