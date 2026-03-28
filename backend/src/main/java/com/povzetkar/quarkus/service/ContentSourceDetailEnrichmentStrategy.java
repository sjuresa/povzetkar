package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentSource;
import java.util.Optional;

public interface ContentSourceDetailEnrichmentStrategy {
    void enrich();
    void enrich(ContentSource source);
    void refresh();
    Optional<ContentSourceDetailService.ContentSourceDetails> fetchDetails(String identifier);
}
