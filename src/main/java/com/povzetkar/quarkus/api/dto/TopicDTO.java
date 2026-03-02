package com.povzetkar.quarkus.api.dto;

import java.time.Instant;

public record TopicDTO(
        Long id,
        String name,
        boolean enabled,
        Instant lastRunDate) {
}
