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
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/ui")
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

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listTopics() {
        List<User> usersWithTopics = User.findAllWithTopics();
        Map<User, List<Topic>> topicsByUser = usersWithTopics.stream()
                .filter(user -> !user.topics.isEmpty())
                .collect(Collectors.toMap(user -> user, user -> user.topics.stream()
                        .sorted(Comparator.comparing(topic -> topic.name))
                        .toList()));
        return topics.data("topicsByUser", topicsByUser);
    }

    @GET
    @Path("/briefings/{briefingId}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showBriefing(@PathParam("briefingId") Long briefingId) {
        Briefing briefing = Briefing.findByIdWithAssociations(briefingId).orElseThrow(NotFoundException::new);

        RawString metaSummaryHtml = markdownToHtml(briefing.metaSummary);

        List<BriefingDetailDto.SummaryDto> summaryDtos = briefing.summaries.stream()
                .sorted(Comparator.comparing(summary -> summary.contentItem.publishedAt))
                .map(summary -> new BriefingDetailDto.SummaryDto(summary, markdownToHtml(summary.summaryText)))
                .toList();

        BriefingDetailDto dto = new BriefingDetailDto(briefing, metaSummaryHtml, summaryDtos);

        return briefingDetail.data("briefing", dto);
    }

    private RawString markdownToHtml(String markdown) {
        if (markdown == null || markdown.isBlank()) {
            return new RawString("");
        }
        return new RawString(htmlRenderer.render(markdownParser.parse(markdown)));
    }
}
