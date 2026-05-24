package com.povzetkar.quarkus.service.transcript;

import java.util.List;

public record SupadataJobStatusResponse(String status, List<SupadataChunk> content, String lang, String error) {
}
