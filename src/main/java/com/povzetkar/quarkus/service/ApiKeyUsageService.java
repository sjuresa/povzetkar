package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class ApiKeyUsageService {

    private final Clock clock;
    private final ZoneId zoneId; // Store the ZoneId

    @Inject
    public ApiKeyUsageService(
            @ConfigProperty(name = "povzetkar.api-usage.timezone", defaultValue = "America/Los_Angeles") String timezoneId) {
        ZoneId configuredZoneId = ZoneId.of(timezoneId);
        this.clock = Clock.system(configuredZoneId);
        this.zoneId = configuredZoneId; // Initialize the zoneId field
    }

    // Constructor for testing purposes
    public ApiKeyUsageService(Clock clock) {
        this.clock = clock;
        this.zoneId = clock.getZone(); // Derive zoneId from the provided clock for tests
    }

    @Transactional
    public void incrementUsage(String apiKeyName) {
        LocalDate today = LocalDate.now(clock);
        ApiKeyUsage usage = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, today)
                .firstResult();

        if (usage == null) {
            usage = new ApiKeyUsage(apiKeyName, today);
        }

        usage.incrementUsageCount();
        usage.persist();
    }

    public List<ApiKeyUsage> getUsageForToday() {
        LocalDate today = LocalDate.now(clock);
        return ApiKeyUsage.list("usageDate", today);
    }

    public String getTimezoneId() {
        return zoneId.getId();
    }
}
