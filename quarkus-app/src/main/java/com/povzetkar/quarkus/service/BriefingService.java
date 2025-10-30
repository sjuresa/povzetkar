package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.time.LocalDate;

@ApplicationScoped
public class BriefingService {

    private final BriefingGenerator briefingGenerator;

    @Inject
    public BriefingService(BriefingGenerator briefingGenerator) {
        this.briefingGenerator = briefingGenerator;
    }

    public Briefing generateBriefing(Topic topic, LocalDate briefingDate) {
        return briefingGenerator.generateBriefing(topic, briefingDate);
    }
}
