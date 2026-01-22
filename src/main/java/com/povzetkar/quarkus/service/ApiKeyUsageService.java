package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@ApplicationScoped
public class ApiKeyUsageService {

    private static final ZoneId PST_ZONE_ID = ZoneId.of("America/Los_Angeles");
    private final Clock clock;

    public ApiKeyUsageService() {
        this.clock = Clock.system(PST_ZONE_ID);
    }

    // Constructor for testing purposes
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

        usage.usageCount++;
        usage.persist();
    }

    public List<ApiKeyUsage> getUsageForToday() {
        LocalDate today = LocalDate.now(clock);
        return ApiKeyUsage.list("usageDate", today);
    }
}
