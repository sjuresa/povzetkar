package com.povzetkar.quarkus.ui.util;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class DateTimeServiceTest {

    @Inject
    DateTimeService dateTimeService;

    @Test
    void testFormatInstant() {
        // 2023-10-27 10:00:00 UTC
        Instant instant = Instant.parse("2023-10-27T10:00:00Z");
        
        // Expected: 2023-10-27 12:00:00 (Europe/Ljubljana is UTC+2 in October)
        // Note: The format is defined in application.properties as "dd.MM.yyyy HH:mm:ss"
        // But for test profile it might be different or default.
        // Let's check the default behavior or configured behavior.
        // The default in DateTimeService is "yyyy-MM-dd HH:mm:ss" but application.properties overrides it.
        // In test profile, we should probably rely on what's injected.
        
        // However, since we are running @QuarkusTest, it loads application.properties.
        // In application.properties: povzetkar.ui.datetime.format=dd.MM.yyyy HH:mm:ss
        // And timezone is Europe/Ljubljana.
        
        String formatted = dateTimeService.formatInstant(instant);
        assertEquals("27.10.2023 12:00:00", formatted);
    }

    @Test
    void testFormatLocalDate() {
        LocalDate date = LocalDate.of(2023, 10, 27);
        // In application.properties: povzetkar.ui.date.format=dd.MM.yyyy
        
        String formatted = dateTimeService.formatLocalDate(date);
        assertEquals("27.10.2023", formatted);
    }

    @Test
    void testFormatInstantNull() {
        assertEquals("N/A", dateTimeService.formatInstant(null));
    }

    @Test
    void testFormatLocalDateNull() {
        assertEquals("N/A", dateTimeService.formatLocalDate(null));
    }
}
