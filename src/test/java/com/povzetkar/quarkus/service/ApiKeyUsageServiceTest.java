package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class ApiKeyUsageServiceTest {

    @Inject
    ApiKeyUsageService apiKeyUsageService;

    // This test uses the real service, which uses the system clock for PST.
    // To test date-specific logic, we'd need to inject a mock Clock.
    // The service is designed to allow this.

    @AfterEach
    @Transactional
    void tearDown() {
        ApiKeyUsage.deleteAll();
    }

    @Test
    @DisplayName("incrementUsage should create a new record for a new API key and date")
    @Transactional
    void incrementUsage_shouldCreateNewRecord() {
        // Arrange
        String apiKeyName = "test-key-1";
        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));

        // Act
        apiKeyUsageService.incrementUsage(apiKeyName);

        // Assert
        ApiKeyUsage usage = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, today).firstResult();
        assertNotNull(usage);
        assertEquals(1, usage.getUsageCount());
    }

    @Test
    @DisplayName("incrementUsage should increment the count for an existing record")
    @Transactional
    void incrementUsage_shouldIncrementExistingRecord() {
        // Arrange
        String apiKeyName = "test-key-1";
        apiKeyUsageService.incrementUsage(apiKeyName); // First usage

        // Act
        apiKeyUsageService.incrementUsage(apiKeyName); // Second usage

        // Assert
        LocalDate today = LocalDate.now(ZoneId.of("America/Los_Angeles"));
        ApiKeyUsage usage = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, today).firstResult();
        assertNotNull(usage);
        assertEquals(2, usage.getUsageCount());
        assertEquals(1, ApiKeyUsage.count());
    }

    @Test
    @DisplayName("getUsageForToday should return only records for the current date")
    @Transactional
    void getUsageForToday_shouldReturnOnlyTodayRecords() {
        // Arrange - Use a controllable clock
        ZoneId pstZone = ZoneId.of("America/Los_Angeles");
        LocalDate today = LocalDate.of(2023, 10, 27);
        LocalDate yesterday = today.minusDays(1);

        Clock fixedClock = Clock.fixed(today.atStartOfDay(pstZone).toInstant(), pstZone);
        ApiKeyUsageService testService = new ApiKeyUsageService(fixedClock);

        // Manually create records for different dates
        createUsageRecord("key-today", today, 5);
        createUsageRecord("key-yesterday", yesterday, 10);

        // Act
        List<ApiKeyUsage> todayUsage = testService.getUsageForToday();

        // Assert
        assertEquals(1, todayUsage.size());
        assertEquals("key-today", todayUsage.getFirst().apiKeyName);
        assertEquals(5, todayUsage.getFirst().getUsageCount());
    }

    @Test
    @DisplayName("incrementUsage should create a new record for a new day")
    @Transactional // Added @Transactional
    void incrementUsage_shouldCreateRecordForNewDay() {
        // Arrange
        String apiKeyName = "test-key-new-day";
        ZoneId pstZone = ZoneId.of("America/Los_Angeles");

        // Day 1
        LocalDate day1 = LocalDate.of(2023, 10, 27);
        Clock day1Clock = Clock.fixed(day1.atStartOfDay(pstZone).toInstant(), pstZone);
        ApiKeyUsageService serviceDay1 = new ApiKeyUsageService(day1Clock);
        serviceDay1.incrementUsage(apiKeyName);
        serviceDay1.incrementUsage(apiKeyName);

        // Day 2
        LocalDate day2 = LocalDate.of(2023, 10, 28);
        Clock day2Clock = Clock.fixed(day2.atStartOfDay(pstZone).toInstant(), pstZone);
        ApiKeyUsageService serviceDay2 = new ApiKeyUsageService(day2Clock);

        // Act
        serviceDay2.incrementUsage(apiKeyName);

        // Assert
        long totalRecords = ApiKeyUsage.count("apiKeyName", apiKeyName);
        assertEquals(2, totalRecords, "Should have two separate records for two different days");

        ApiKeyUsage usageDay1 = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, day1).firstResult();
        assertNotNull(usageDay1);
        assertEquals(2, usageDay1.getUsageCount());

        ApiKeyUsage usageDay2 = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, day2).firstResult();
        assertNotNull(usageDay2);
        assertEquals(1, usageDay2.getUsageCount());
    }

    @Transactional
    void createUsageRecord(String apiKeyName, LocalDate date, long count) {
        ApiKeyUsage usage = new ApiKeyUsage(apiKeyName, date);
        for (int i = 0; i < count; i++) {
            usage.incrementUsageCount();
        }
        usage.persist();
    }
}
