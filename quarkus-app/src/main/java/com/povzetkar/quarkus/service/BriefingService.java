package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class BriefingService {

    private static final String BRIEFING_TITLE_FORMAT = "Daily Briefing for %s";
    private final VideoSummaryService videoSummaryService;
    private static final Logger logger = LoggerFactory.getLogger(BriefingService.class);

    @Inject
    public BriefingService(VideoSummaryService videoSummaryService) {
        this.videoSummaryService = videoSummaryService;
    }

    public Briefing generateBriefing(Topic topic, LocalDate briefingDate) {
        // 1. Query the database for all Summary entities for the given topic within that period.
        List<Summary> summaries = QuarkusTransaction.requiringNew().call(() -> Summary.findByTopicAndDateRange(topic, briefingDate));

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

        return QuarkusTransaction.requiringNew().call(() -> {
            // 4. Check if a briefing already exists for this topic and date, and lock the row to prevent race conditions.
            Optional<Briefing> existingBriefingOptional = Briefing.find("topic.id = ?1 and briefingDate = ?2", topic.id, briefingDate)
                    .withLock(LockModeType.PESSIMISTIC_WRITE)
                    .firstResultOptional();

            Briefing briefing;
            if (existingBriefingOptional.isPresent()) {
                briefing = existingBriefingOptional.get(); // Already managed if fetched within this transaction
                logger.info("Updating existing briefing for topic '{}' on {}.", topic.name, briefingDate);
                briefing.metaSummary = metaSummary;
            } else {
                logger.info("Creating new briefing for topic '{}' on {}.", topic.name, briefingDate);
                briefing = new Briefing(topic, String.format(BRIEFING_TITLE_FORMAT, briefingDate), metaSummary, briefingDate);
            }
            briefing.persist();

            // The summaries are detached after the first transaction. We re-query them to attach to the new persistence context.
            List<Long> summaryIds = summaries.stream().map(s -> s.id).toList();
            briefing.summaries.addAll(Summary.list("id in ?1", summaryIds));
            // No persist() needed here for existing briefings, changes will be flushed automatically.
            return briefing;
        });
    }
}
