package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.Summary;

import java.time.LocalDate;

public class SummaryBuilder {
    private Long id;
    private String summaryText = "### Executive Summary\nDefault Content Summary\n###\nFull markdown content";
    private ContentItem contentItem;
    private LocalDate briefingDate = LocalDate.now();

    private SummaryBuilder() {
    }

    public static SummaryBuilder aSummary() {
        return new SummaryBuilder();
    }

    public SummaryBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public SummaryBuilder withSummaryText(String summaryText) {
        this.summaryText = summaryText;
        return this;
    }

    public SummaryBuilder withContentItem(ContentItem contentItem) {
        this.contentItem = contentItem;
        return this;
    }

    public SummaryBuilder withBriefingDate(LocalDate briefingDate) {
        this.briefingDate = briefingDate;
        return this;
    }

    public Summary build() {
        Summary summary = new Summary(summaryText, contentItem, briefingDate);
        summary.id = id;
        return summary;
    }
}
