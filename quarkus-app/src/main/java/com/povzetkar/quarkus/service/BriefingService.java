package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@ApplicationScoped
public class BriefingService {

    private final VideoSummaryService videoSummaryService;
    private static final Logger logger = LoggerFactory.getLogger(BriefingService.class);

    @Inject
    public BriefingService(VideoSummaryService videoSummaryService) {
        this.videoSummaryService = videoSummaryService;
    }

    @Transactional
    public Briefing generateBriefing(Topic topic, LocalDate briefingDate) {
        // 1. Query the database for all Summary entities for the given topic within that period.
        List<Summary> summaries = Summary.findByTopicAndDateRange(topic, briefingDate);

        if (summaries.isEmpty()) {
            logger.info("No new summaries found for topic '{}' on {}. Skipping briefing generation.", topic.name, briefingDate);
            return null;
        }

        // 2. Format these Summary entities into a single rawSummaries string.
        String rawSummaries = summaries.stream()
                .map(s -> s.summaryText)
                .collect(Collectors.joining("\n---\n"));

        // 3. Use the GenAI service to create a meta-summary from the collected summaries.
        String metaSummary = videoSummaryService.createBriefingFromSummaries(rawSummaries, briefingDate.toString());

        // 4. Create a new Briefing entity, linking it to the parent Topic and all relevant summaries, then persisting it.
        Briefing briefing = new Briefing(topic, "Daily Briefing for " + briefingDate, metaSummary, briefingDate);
        briefing.summaries.addAll(summaries);

        try {
            briefing.persist();
            return briefing;
        } catch (PersistenceException e) {
            // This is expected if another process created the briefing in the meantime due to the unique constraint.
            logger.warn("Briefing for topic '{}' on {} already exists. Another process likely created it. Skipping generation.", topic.name, briefingDate);
            return null;
        }
    }
}
