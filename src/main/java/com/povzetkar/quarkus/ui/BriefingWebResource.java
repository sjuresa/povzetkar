package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
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

import static com.povzetkar.quarkus.ui.TopicResource.TOPICS_PATH;

@ApplicationScoped
public class BriefingWebResource {

    private static final String INVALID_BRIEFING_ID_FORMAT = "Invalid briefing ID format.";
    private static final String BRIEFING_NOT_FOUND_MSG = "Briefing with id %d not found.";

    private final ViewUtils viewUtils;

    private final Template briefingDetail;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public BriefingWebResource(@Location("briefing-detail.html") Template briefingDetail, ViewUtils viewUtils) {
        this.briefingDetail = briefingDetail;
        this.viewUtils = viewUtils;

        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @Route(path = "/ui/briefings/:briefingId", methods = Route.HttpMethod.GET)
    @Blocking
    public void showBriefing(RoutingContext context) {
        Optional<Long> briefingIdOptional = WebUtils.getLongPathParam(context, "briefingId", INVALID_BRIEFING_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (briefingIdOptional.isEmpty()) {
            return;
        }
        Long briefingId = briefingIdOptional.get();

        Optional<Briefing> briefingOptional = Briefing.findByIdWithAssociations(briefingId);
        if (briefingOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(BRIEFING_NOT_FOUND_MSG, briefingId));
            return;
        }
        Briefing briefing = briefingOptional.get();

        RawString metaSummaryHtml = markdownToHtml(briefing.metaSummary);

        List<BriefingDetailDto.SummaryDto> summaryDtos = briefing.summaries.stream()
                .sorted(Comparator.comparing(summary -> summary.contentItem.publishedAt))
                .map(summary -> new BriefingDetailDto.SummaryDto(summary, markdownToHtml(summary.summaryText)))
                .toList();

        BriefingDetailDto dto = new BriefingDetailDto(briefing, metaSummaryHtml, summaryDtos);

        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("briefing", dto);
        data.put("breadcrumbs", List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb("Topics", TOPICS_PATH),
                new Breadcrumb(briefing.topic.name, TopicResource.topicBriefingsPath(briefing.topic.id)),
                new Breadcrumb("Briefing", context.request().uri())
        ));

        context.response().end(briefingDetail.instance().data(data).render());
    }

    private RawString markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RawString("");
        }
        return new RawString(htmlRenderer.render(markdownParser.parse(markdown)));
    }
}
