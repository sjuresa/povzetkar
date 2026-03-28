package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;

import java.time.LocalDate;
import java.util.List;

public interface BriefingGenerator {
    List<Summary> getSummariesForBriefing(Long topicId, LocalDate briefingDate);
    String getMetaSummaryFromAI(Topic topic, List<Summary> summaries, LocalDate briefingDate);
    Briefing generateBriefing(Topic topic, LocalDate briefingDate, List<Summary> summaries, String metaSummary);
}
