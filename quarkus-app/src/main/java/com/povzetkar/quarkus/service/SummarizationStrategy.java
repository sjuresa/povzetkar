package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;

import java.time.LocalDate;

public interface SummarizationStrategy {
    boolean supports(ContentItem item);
    boolean summarize(ContentItem item, LocalDate orchestrationDate);
}
