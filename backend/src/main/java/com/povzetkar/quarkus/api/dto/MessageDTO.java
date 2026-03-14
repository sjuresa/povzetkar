package com.povzetkar.quarkus.api.dto;
 
import org.eclipse.microprofile.openapi.annotations.media.Schema;
 
@Schema(description = "Simple message response")
public record MessageDTO(
    @Schema(description = "The message text")
    String message
) {
}
