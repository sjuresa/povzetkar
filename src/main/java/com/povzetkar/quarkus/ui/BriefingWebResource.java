package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.data.MutableDataSet;
import io.quarkus.qute.Location;
import io.quarkus.qute.RawString;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@ApplicationScoped
public class BriefingWebResource {

    private final Template topics;
    private final Template briefingDetail;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public BriefingWebResource(@Location("topics.html") Template topics, @Location("briefing-detail.html") Template briefingDetail) {
        this.topics = topics;
        this.briefingDetail = briefingDetail;

        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @Route(path = "/ui", methods = Route.HttpMethod.GET)
    @Blocking
    public void listTopics(RoutingContext context) {
        List<User> usersWithTopics = User.findAllWithTopics();
        Map<User, List<Topic>> topicsByUser = usersWithTopics.stream()
                .filter(user -> !user.topics.isEmpty())
                .collect(Collectors.toMap(user -> user, user -> user.topics.stream()
                        .sorted(Comparator.comparing(topic -> topic.name))
                        .toList()));
        context.response()
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .end(topics.data("topicsByUser", topicsByUser).render());
    }

    @Route(path = "/ui/briefings/:briefingId", methods = Route.HttpMethod.GET)
    @Blocking
    public void showBriefing(RoutingContext context) {
        long briefingId;
        try {
            briefingId = Long.parseLong(context.pathParam("briefingId"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid briefing ID format.");
            return;
        }
        Optional<Briefing> briefingOptional = Briefing.findByIdWithAssociations(briefingId);
        if (briefingOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format("Briefing with id %d not found.", briefingId));
            return;
        }
        Briefing briefing = briefingOptional.get();

        RawString metaSummaryHtml = markdownToHtml(briefing.metaSummary);

        List<BriefingDetailDto.SummaryDto> summaryDtos = briefing.summaries.stream()
                .sorted(Comparator.comparing(summary -> summary.contentItem.publishedAt))
                .map(summary -> new BriefingDetailDto.SummaryDto(summary, markdownToHtml(summary.summaryText)))
                .toList();

        BriefingDetailDto dto = new BriefingDetailDto(briefing, metaSummaryHtml, summaryDtos);

        context.response().end(briefingDetail.data("briefing", dto).render());
    }

    private RawString markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RawString("");
        }
        return new RawString(htmlRenderer.render(markdownParser.parse(markdown)));
    }
}
