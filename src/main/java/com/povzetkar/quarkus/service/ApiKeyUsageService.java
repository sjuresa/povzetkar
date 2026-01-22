package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import io.quarkus.panache.common.Parameters;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class ApiKeyUsageService {

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

    @Transactional
    public void incrementUsage(String apiKeyName) {
        LocalDate today = LocalDate.now(clock);

        // Pessimistic lock to prevent race conditions
        ApiKeyUsage usage = ApiKeyUsage.find(
                        "apiKeyName = :apiKeyName AND usageDate = :usageDate",
                        Parameters.with("apiKeyName", apiKeyName).and("usageDate", today))
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();

        if (usage != null) {
            usage.incrementUsageCount();
        } else {
            usage = new ApiKeyUsage(apiKeyName, today);
            usage.incrementUsageCount(); // Set initial count to 1
            entityManager.persist(usage);
        }
    }

    public List<ApiKeyUsage> getUsageForToday() {
        LocalDate today = LocalDate.now(clock);
        return ApiKeyUsage.list("usageDate", today);
    }

    public String getTimezoneId() {
        return zoneId.getId();
    }
}
