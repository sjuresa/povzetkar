package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;

import java.time.LocalDate;

public interface BriefingGenerator {
    Briefing generateBriefing(Topic topic, LocalDate orchestrationDate);
}
