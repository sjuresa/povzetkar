package com.povzetkar.quarkus.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.Instant;

@RegisterForReflection
@Schema(name = "PublicSummary", description = "Privacy-preserving summary data for public access")
public record PublicSummaryDTO(
    String title,
    String authorName,
    String sourceUrl,
    Instant publishedAt,
    String summaryText
) {
}
