package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import jakarta.annotation.Priority;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;

@ApplicationScoped
@Priority(0) // Lowest priority, acts as a fallback
public class NullSummarizationStrategy implements SummarizationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NullSummarizationStrategy.class);

    @Override
    public boolean summarize(ContentItem item, LocalDate orchestrationDate) {
        logger.warn("No specific summarization strategy found for content item type: {}. Marking as FAILED.", item.getClass().getName());
        item.processingStatus = ProcessingStatus.FAILED;
        return false;
    }

    @Override
    public boolean supports(ContentItem item) {
        // This strategy supports all types, but its low priority means it will only be chosen if no other strategy matches.
        return true;
    }
}
