package com.povzetkar.quarkus.api.dto;

import java.time.Instant;

public record SseEventDTO(String type, Object data, Instant timestamp) {
    public static SseEventDTO of(String type, Object data) {
        return new SseEventDTO(type, data, Instant.now());
    }
}
