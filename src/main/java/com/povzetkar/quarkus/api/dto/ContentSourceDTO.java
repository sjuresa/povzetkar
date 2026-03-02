package com.povzetkar.quarkus.api.dto;

public record ContentSourceDTO(
        Long id,
        String platform,
        String sourceIdentifier,
        String name,
        String description,
        int subscriberCount,
        String sourceUrl) {
}
