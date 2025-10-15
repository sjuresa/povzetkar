package com.povzetkar.quarkus.service;

public interface ContentSourceDetailEnrichmentStrategy {
    void enrich();
    void refresh();
}
