package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.povzetkar.quarkus.ui.TopicResource.TOPICS_PATH;

@ApplicationScoped
public class SummaryResource {

    private static final String INVALID_SUMMARY_ID_FORMAT = "Invalid summary ID format.";
    private static final String SUMMARY_NOT_FOUND_MSG = "Summary with id %d not found.";

    private final ViewUtils viewUtils;
    private final Template summaryDetail;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;
    private final DateTimeFormatter dateFormatter;

    public SummaryResource(@Location("summary-detail.html") Template summaryDetail,
                           ViewUtils viewUtils,
                           @ConfigProperty(name = "povzetkar.ui.date.format") String dateFormat) {
        this.summaryDetail = summaryDetail;
        this.viewUtils = viewUtils;
        this.dateFormatter = DateTimeFormatter.ofPattern(dateFormat);

        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @Route(path = "/ui/summaries/:summaryId", methods = Route.HttpMethod.GET)
    @Blocking
    @Parameter(name = "summaryId", in = ParameterIn.PATH, required = true, schema = @Schema(type = SchemaType.INTEGER))
    public void showSummary(RoutingContext context) {
        Optional<Long> summaryIdOptional = WebUtils.getLongPathParam(context, "summaryId", INVALID_SUMMARY_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (summaryIdOptional.isEmpty()) {
            return;
        }
        Long summaryId = summaryIdOptional.get();

        Optional<Summary> summaryOptional = Summary.find("from Summary s join fetch s.contentItem ci join fetch ci.contentSource cs where s.id = ?1", summaryId).firstResultOptional();
        if (summaryOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(SUMMARY_NOT_FOUND_MSG, summaryId));
            return;
        }
        Summary summary = summaryOptional.get();

        Optional<Briefing> briefingOptional = Briefing.find("select b from Briefing b join fetch b.topic join b.summaries s where s.id = ?1", summaryId).firstResultOptional();
        
        RawString summaryHtml = markdownToHtml(summary.summaryText);

        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("summary", summary);
        data.put("summaryHtml", summaryHtml);
        data.put("breadcrumbs", buildSummaryBreadcrumbs(context, briefingOptional.orElse(null)));

        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(summaryDetail.instance().data(data).render());
    }

    private List<Breadcrumb> buildSummaryBreadcrumbs(RoutingContext context, Briefing briefing) {
        List<Breadcrumb> breadcrumbs = new ArrayList<>();
        breadcrumbs.add(ViewUtils.HOME_BREADCRUMB);
        breadcrumbs.add(new Breadcrumb("Topics", TOPICS_PATH));

        if (briefing != null) {
            breadcrumbs.add(new Breadcrumb(briefing.topic.name, TopicResource.topicBriefingsPath(briefing.topic.id)));
            breadcrumbs.add(new Breadcrumb("Briefing " + briefing.briefingDate.format(dateFormatter), "/ui/briefings/" + briefing.id));
            breadcrumbs.add(new Breadcrumb("Summary", context.request().uri()));
        } else {
            // If no briefing, simplify the breadcrumb to avoid arbitrary topic selection
            breadcrumbs.add(new Breadcrumb("Summary", context.request().uri()));
        }
        return breadcrumbs;
    }

    private RawString markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RawString("");
        }
        return new RawString(htmlRenderer.render(markdownParser.parse(markdown)));
    }
}
