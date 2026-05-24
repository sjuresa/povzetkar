package com.povzetkar.quarkus.service.transcript;

import java.util.List;

public record SupadataTranscriptResponse(List<SupadataChunk> content, String lang, List<String> availableLangs) {
}
