package com.povzetkar.quarkus.api.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@RegisterForReflection
@Schema(name = "PublicShare", description = "Polymorphic wrapper for public shared content")
public record PublicShareDTO(
    @Schema(required = true, description = "The type of content being shared")
    PublicInsightType type,
    
    @Schema(
        required = true, 
        oneOf = {BriefingDetailDTO.class, PublicSummaryDTO.class},
        description = "The shared content payload (Briefing or Summary)"
    )
    Object data
) {
}
