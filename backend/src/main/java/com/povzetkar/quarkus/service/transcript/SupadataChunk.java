package com.povzetkar.quarkus.service.transcript;

public record SupadataChunk(String text, long offset, long duration, String lang) {
}
