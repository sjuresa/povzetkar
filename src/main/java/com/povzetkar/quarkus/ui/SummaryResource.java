package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Summary;
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

import java.util.Optional;

import static com.povzetkar.quarkus.util.WebUtils.getLongPathParam;

@ApplicationScoped
public class SummaryResource {

    private static final String INVALID_SUMMARY_ID_FORMAT = "Invalid summary ID format.";
    private static final String SUMMARY_NOT_FOUND_MSG = "Summary with id %d not found.";

    private final Template summaryDetail;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public SummaryResource(@Location("summary-detail.html") Template summaryDetail) {
        this.summaryDetail = summaryDetail;

        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @Route(path = "/ui/summaries/:summaryId", methods = Route.HttpMethod.GET)
    @Blocking
    public void showSummary(RoutingContext context) {
        Long summaryId = getLongPathParam(context, "summaryId", INVALID_SUMMARY_ID_FORMAT);
        if (summaryId == null) {
            return;
        }
        Optional<Summary> summaryOptional = Summary.findByIdOptional(summaryId);
        if (summaryOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(SUMMARY_NOT_FOUND_MSG, summaryId));
            return;
        }
        Summary summary = summaryOptional.get();

        RawString summaryHtml = markdownToHtml(summary.summaryText);

        context.response().end(summaryDetail.data("summary", summary).data("summaryHtml", summaryHtml).render());
    }

    private RawString markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RawString("");
        }
        return new RawString(htmlRenderer.render(markdownParser.parse(markdown)));
    }
}
