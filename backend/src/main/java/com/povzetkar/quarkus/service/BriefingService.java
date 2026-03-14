package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.util.List;

@ApplicationScoped
public class BriefingService {

    private final BriefingGenerator briefingGenerator;

    @Inject
    public BriefingService(BriefingGenerator briefingGenerator) {
        this.briefingGenerator = briefingGenerator;
    }

    public List<Summary> getSummariesForBriefing(Topic topic, LocalDate briefingDate) {
        return briefingGenerator.getSummariesForBriefing(topic, briefingDate);
    }

    public String getMetaSummaryFromAI(Topic topic, List<Summary> summaries, LocalDate briefingDate) {
        return briefingGenerator.getMetaSummaryFromAI(topic, summaries, briefingDate);
    }

    public Briefing generateBriefing(Topic topic, LocalDate briefingDate, List<Summary> summaries, String metaSummary) {
        return briefingGenerator.generateBriefing(topic, briefingDate, summaries, metaSummary);
    }
}
