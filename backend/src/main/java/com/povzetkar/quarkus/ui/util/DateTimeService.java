package com.povzetkar.quarkus.ui.util;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

@ApplicationScoped
public class DateTimeService {

    @ConfigProperty(name = "povzetkar.ui.timezone", defaultValue = "Europe/Ljubljana")
    String uiTimezone;

    @ConfigProperty(name = "povzetkar.ui.datetime.format", defaultValue = "yyyy-MM-dd HH:mm:ss")
    String uiDateTimeFormat;

    @ConfigProperty(name = "povzetkar.ui.date.format", defaultValue = "yyyy-MM-dd")
    String uiDateFormat;

    public String formatInstant(Instant instant) {
        if (instant == null) {
            return "N/A";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(uiDateTimeFormat)
                .withZone(ZoneId.of(uiTimezone));
        return formatter.format(instant);
    }

    public String formatLocalDate(LocalDate localDate) {
        if (localDate == null) {
            return "N/A";
        }
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(uiDateFormat);
        return formatter.format(localDate);
    }
}
