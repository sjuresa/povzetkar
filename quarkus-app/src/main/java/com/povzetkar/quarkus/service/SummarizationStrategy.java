package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;

public interface SummarizationStrategy {
    boolean supports(ContentItem item);
    boolean summarize(ContentItem item);
}
