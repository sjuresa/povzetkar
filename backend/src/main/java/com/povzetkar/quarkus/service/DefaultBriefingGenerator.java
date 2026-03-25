package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class DefaultBriefingGenerator implements BriefingGenerator {

    private static final String BRIEFING_TITLE_FORMAT = "Daily Briefing for %s";
    private final VideoSummaryService videoSummaryService;
    private static final Logger logger = LoggerFactory.getLogger(DefaultBriefingGenerator.class);

    @Inject
    public DefaultBriefingGenerator(VideoSummaryService videoSummaryService) {
        this.videoSummaryService = videoSummaryService;
    }

    @Override
    public List<Summary> getSummariesForBriefing(Topic topic, LocalDate briefingDate) {
        return Summary.findByTopicAndDateRange(topic, briefingDate);
    }

    /**
     * Generates a meta-summary from a list of summaries using a blocking AI call.
     * This method is intentionally non-transactional.
     */
    public String getMetaSummaryFromAI(Topic topic, List<Summary> summaries, LocalDate briefingDate) {
        if (summaries == null || summaries.isEmpty()) {
            return null;
        }
        String rawSummaries = summaries.stream()
                .map(s -> s.summaryText)
                .collect(Collectors.joining("\n---\n"));

        logger.info("Generating meta-summary for topic '{}' on {} from {} summaries ({} characters).",
                topic.name, briefingDate, summaries.size(), rawSummaries.length());

        // Acquire client and make the blocking call here, outside any transaction.
        GenAiClient client = videoSummaryService.acquireClient();
        return videoSummaryService.createBriefingFromSummaries(client, rawSummaries, briefingDate.format(DateTimeFormatter.ofPattern("d.M.yyyy")), topic.name, topic.promptTemplate);
    }

    /**
     * Creates or updates a briefing in the database with a pre-generated meta-summary.
     * This method is designed to be called within a transaction.
     */
    public Briefing generateBriefing(Topic topic, LocalDate briefingDate, List<Summary> summaries, String metaSummary) {
        if (summaries.isEmpty()) {
            logger.info("No summaries provided for topic '{}' on {}. Skipping briefing generation.", topic.name, briefingDate);
            return null;
        }
        if (metaSummary == null || metaSummary.isBlank()) {
            logger.warn("Meta-summary is blank for topic '{}' on {}. Skipping briefing generation.", topic.name, briefingDate);
            return null;
        }

        Optional<Briefing> existingBriefingOptional = Briefing.find("topic.id = ?1 and briefingDate = ?2", topic.id, briefingDate)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResultOptional();

        Briefing briefing;
        if (existingBriefingOptional.isPresent()) {
            briefing = existingBriefingOptional.get();
            logger.info("Updating existing briefing for topic '{}' on {}.", topic.name, briefingDate);
            briefing.metaSummary = metaSummary;
            briefing.summaries.clear();
        } else {
            logger.info("Creating new briefing for topic '{}' on {}.", topic.name, briefingDate);
            briefing = new Briefing(topic, String.format(BRIEFING_TITLE_FORMAT, briefingDate), metaSummary, briefingDate);
            briefing.persist();
        }
        List<Long> summaryIds = summaries.stream().map(s -> s.id).toList();
        briefing.summaries.addAll(Summary.list("id in ?1", summaryIds));
        logger.info("Briefing generated for topic '{}' on {} with {} summaries.", topic.name, briefingDate, briefing.summaries.size());

        return briefing;
    }
}
