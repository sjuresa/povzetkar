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

    @Inject
    public ApiKeyUsageService(
            @ConfigProperty(name = "povzetkar.api-usage.timezone", defaultValue = "America/Los_Angeles") String timezoneId) {
        this(Clock.system(ZoneId.of(timezoneId)));
    }

    public ApiKeyUsageService(Clock clock) {
        this.clock = clock;
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
}
