package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

public class BriefingBuilder {
    private Long id;
    private Topic topic;
    private String title = "Default Briefing Title";
    private String metaSummary = "### 1. Overall Executive Summary\nDefault Summary\n###";
    private LocalDate briefingDate = LocalDate.now();
    private Set<Summary> summaries = new HashSet<>();

    private BriefingBuilder() {
    }

    public static BriefingBuilder aBriefing() {
        return new BriefingBuilder();
    }

    public BriefingBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public BriefingBuilder withTopic(Topic topic) {
        this.topic = topic;
        return this;
    }

    public BriefingBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public BriefingBuilder withMetaSummary(String metaSummary) {
        this.metaSummary = metaSummary;
        return this;
    }

    public BriefingBuilder withBriefingDate(LocalDate briefingDate) {
        this.briefingDate = briefingDate;
        return this;
    }

    public BriefingBuilder withSummaries(Set<Summary> summaries) {
        this.summaries = summaries;
        return this;
    }

    public Briefing build() {
        Briefing briefing = new Briefing(topic, title, metaSummary, briefingDate);
        briefing.id = id;
        briefing.summaries = summaries;
        return briefing;
    }
}
