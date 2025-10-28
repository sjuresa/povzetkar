package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.List;

@ApplicationScoped
@Path("/ui/topics")
public class TopicResource {

    public static final String BRIEFINGS = "/briefings";
    public static final String UI_TOPICS = "/ui/topics/";
    public static final String TOPIC = "topic";
    private final Template topicForm;
    private final Template briefingsByTopic;

    public TopicResource(@Location("topic-form.html") Template topicForm, @Location("briefings-by-topic.html") Template briefingsByTopic) {
        this.topicForm = topicForm;
        this.briefingsByTopic = briefingsByTopic;
    }

    @GET
    @Path("/add")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showAddForm() {
        List<User> users = User.listAll();
        return topicForm.data("users", users).data(TOPIC, null);
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addTopic(@FormParam("userId") Long userId, @FormParam("name") String name) {
        User user = User.findById(userId);
        if (user != null) {
            Topic topic = new Topic();
            topic.user = user;
            topic.name = name;
            topic.persist();
        }
        return Response.seeOther(URI.create("/ui")).build();
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showEditForm(@PathParam("id") Long id) {
        Topic topic = Topic.findById(id);
        return topicForm.data(TOPIC, topic);
    }

    @POST
    @Path("/{id}")
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateTopic(@PathParam("id") Long id, @FormParam("name") String name) {
        Topic topic = Topic.findById(id);
        if (topic != null) {
            topic.name = name;
            topic.persist();
        }
        return Response.seeOther(URI.create("/ui")).build();
    }

    @POST
    @Path("/{id}/delete")
    @Transactional
    public Response deleteTopic(@PathParam("id") Long id) {
        Topic.deleteById(id);
        return Response.seeOther(URI.create("/ui")).build();
    }

    @GET
    @Path("/{topicId}/briefings")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listBriefingsByTopic(@PathParam("topicId") Long topicId) {
        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        Topic topic = query.firstResultOptional().orElseThrow(NotFoundException::new);
        List<Briefing> briefings = Briefing.find("topic = ?1 order by briefingDate desc", topic).list();
        List<ContentSource> allContentSources = ContentSource.listAll();
        return briefingsByTopic.data(TOPIC, topic)
                .data("briefings", briefings)
                .data("allContentSources", allContentSources);
    }

    @POST
    @Path("/{topicId}/content-sources")
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addContentSourceToTopic(@PathParam("topicId") Long topicId, @FormParam("contentSourceId") Long contentSourceId) {
        if (contentSourceId == null) {
            return Response.seeOther(URI.create(UI_TOPICS + topicId + BRIEFINGS)).build();
        }
        Topic topic = Topic.findById(topicId);
        ContentSource contentSource = ContentSource.findById(contentSourceId);
        if (topic != null && contentSource != null) {
            topic.contentSources.add(contentSource);
            topic.persist();
        }
        return Response.seeOther(URI.create(UI_TOPICS + topicId + BRIEFINGS)).build();
    }

    @POST
    @Path("/{topicId}/content-sources/{contentSourceId}/delete")
    @Transactional
    public Response removeContentSourceFromTopic(@PathParam("topicId") Long topicId, @PathParam("contentSourceId") Long contentSourceId) {
        Topic topic = Topic.findById(topicId);
        ContentSource contentSource = ContentSource.findById(contentSourceId);
        if (topic != null && contentSource != null) {
            topic.contentSources.remove(contentSource);
            topic.persist();
        }
        return Response.seeOther(URI.create(UI_TOPICS + topicId + BRIEFINGS)).build();
    }
}
