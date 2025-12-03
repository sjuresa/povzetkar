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

    public Briefing generateBriefing(Topic topic, LocalDate briefingDate, List<Summary> summaries) {
        return briefingGenerator.generateBriefing(topic, briefingDate, summaries);
    }
}
