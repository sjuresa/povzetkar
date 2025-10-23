package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;

public interface SummarizationStrategy {
    boolean summarize(ContentItem item);
    boolean supports(ContentItem item);
}
