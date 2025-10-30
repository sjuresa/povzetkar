package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import io.quarkus.qute.RawString;

import java.time.LocalDate;
import java.util.List;

public class BriefingDetailDto {
    public final Long id;
    public final String topicName;
    public final LocalDate briefingDate;
    public final RawString metaSummaryHtml;
    public final List<SummaryDto> summaries;

    public BriefingDetailDto(Briefing briefing, RawString metaSummaryHtml, List<SummaryDto> summaries) {
        this.id = briefing.id;
        this.topicName = briefing.topic.name;
        this.briefingDate = briefing.briefingDate;
        this.metaSummaryHtml = metaSummaryHtml;
        this.summaries = summaries;
    }

    public static class SummaryDto {
        public final Long id;
        public final String title;
        public final RawString summaryTextHtml;

        public SummaryDto(Summary summary, RawString summaryTextHtml) {
            this.id = summary.id;
            this.title = summary.contentItem.title;
            this.summaryTextHtml = summaryTextHtml;
        }
    }
}
