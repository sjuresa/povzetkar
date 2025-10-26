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
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Path("/ui")
public class BriefingWebResource {

    private final Template topics;
    private final Template briefingsByTopic;
    private final Template briefingDetail;
    private final Parser markdownParser;
    private final HtmlRenderer htmlRenderer;

    public BriefingWebResource(@Location("topics.html") Template topics, @Location("briefings-by-topic.html") Template briefingsByTopic, @Location("briefing-detail.html") Template briefingDetail) {
        this.topics = topics;
        this.briefingsByTopic = briefingsByTopic;
        this.briefingDetail = briefingDetail;

        MutableDataSet options = new MutableDataSet();
        this.markdownParser = Parser.builder(options).build();
        this.htmlRenderer = HtmlRenderer.builder(options).build();
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listTopics() {
        List<User> users = User.listAll();
        Map<User, List<Topic>> topicsByUser = users.stream()
                .collect(Collectors.toMap(user -> user, user -> Topic.find("user", user).list()));
        return topics.data("topicsByUser", topicsByUser);
    }

    @GET
    @Path("/topics/{topicId}/briefings")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listBriefingsByTopic(@PathParam("topicId") Long topicId) {
        Topic topic = Topic.findById(topicId);
        List<Briefing> briefings = Briefing.find("topic", topic).list();
        return briefingsByTopic.data("topic", topic).data("briefings", briefings);
    }

    @GET
    @Path("/briefings/{briefingId}")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showBriefing(@PathParam("briefingId") Long briefingId) {
        Briefing briefing = Briefing.findById(briefingId);

        RawString metaSummaryHtml = new RawString(htmlRenderer.render(markdownParser.parse(briefing.metaSummary)));

        List<BriefingDetailDto.SummaryDto> summaryDtos = briefing.summaries.stream()
                .map(summary -> new BriefingDetailDto.SummaryDto(summary, new RawString(htmlRenderer.render(markdownParser.parse(summary.summaryText)))))
                .toList();

        BriefingDetailDto dto = new BriefingDetailDto(briefing, metaSummaryHtml, summaryDtos);

        return briefingDetail.data("briefing", dto);
    }
}
