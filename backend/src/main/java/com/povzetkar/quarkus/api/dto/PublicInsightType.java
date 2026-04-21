package com.povzetkar.quarkus.api.dto;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(type = SchemaType.STRING, enumeration = {"BRIEFING", "SUMMARY"}, description = "Supported polymorphic content types for public sharing")
public enum PublicInsightType {
    BRIEFING,
    SUMMARY
}
