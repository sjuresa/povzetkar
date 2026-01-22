package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ApiKeyUsage;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
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

    private ZoneId testZone;
    private Clock fixedClock;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        testZone = ZoneId.of("America/Los_Angeles");
        testDate = LocalDate.of(2023, 1, 1);
        fixedClock = Clock.fixed(testDate.atStartOfDay(testZone).toInstant(), testZone);
    }

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
        ApiKeyUsageService testService = new ApiKeyUsageService(fixedClock);

        // Act
        testService.incrementUsage(apiKeyName);

        // Assert
        ApiKeyUsage usage = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, testDate).firstResult();
        assertNotNull(usage);
        assertEquals(1, usage.getUsageCount());
    }

    @Test
    @DisplayName("incrementUsage should increment the count for an existing record")
    @Transactional
    void incrementUsage_shouldIncrementExistingRecord() {
        // Arrange
        String apiKeyName = "test-key-1";
        ApiKeyUsageService testService = new ApiKeyUsageService(fixedClock);
        testService.incrementUsage(apiKeyName); // First usage

        // Act
        testService.incrementUsage(apiKeyName); // Second usage

        // Assert
        ApiKeyUsage usage = ApiKeyUsage.find("apiKeyName = ?1 and usageDate = ?2", apiKeyName, testDate).firstResult();
        assertNotNull(usage);
        assertEquals(2, usage.getUsageCount());
        assertEquals(1, ApiKeyUsage.count());
    }

    @Test
    @DisplayName("getUsageForToday should return only records for the current date")
    @Transactional
    void getUsageForToday_shouldReturnOnlyTodayRecords() {
        // Arrange - Use a controllable clock
        ApiKeyUsageService testService = new ApiKeyUsageService(fixedClock);
        LocalDate today = LocalDate.now(fixedClock);
        LocalDate yesterday = today.minusDays(1);

        // Manually create records for different dates
        createUsageRecord("key-today", today, 5);
        createUsageRecord("key-yesterday", yesterday, 10);

        // Act
        List<ApiKeyUsage> todayUsage = testService.getUsageForToday();

        // Assert
        assertEquals(1, todayUsage.size());
        assertEquals("key-today", todayUsage.getFirst().getApiKeyName());
        assertEquals(5, todayUsage.getFirst().getUsageCount());
    }

    @Test
    @DisplayName("incrementUsage should create a new record for a new day")
    @Transactional
    void incrementUsage_shouldCreateRecordForNewDay() {
        // Arrange
        String apiKeyName = "test-key-new-day";

        // Day 1
        LocalDate day1 = LocalDate.of(2023, 10, 27);
        Clock day1Clock = Clock.fixed(day1.atStartOfDay(testZone).toInstant(), testZone);
        ApiKeyUsageService serviceDay1 = new ApiKeyUsageService(day1Clock);
        serviceDay1.incrementUsage(apiKeyName);
        serviceDay1.incrementUsage(apiKeyName);

        // Day 2
        LocalDate day2 = LocalDate.of(2023, 10, 28);
        Clock day2Clock = Clock.fixed(day2.atStartOfDay(testZone).toInstant(), testZone);
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

    void createUsageRecord(String apiKeyName, LocalDate date, long count) {
        ApiKeyUsage usage = new ApiKeyUsage(apiKeyName, date);
        for (int i = 0; i < count; i++) {
            usage.incrementUsageCount();
        }
        usage.persist();
    }
}
