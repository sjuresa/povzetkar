package com.povzetkar.quarkus.service;

import java.util.Optional;

public interface ContentSourceDetailEnrichmentStrategy {
    void enrich();
    void refresh();
    Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetails(String identifier);
}
