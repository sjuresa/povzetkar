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
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
@Path("/ui/topics")
public class TopicResource {

    private static final String LIST_BRIEFINGS_BY_TOPIC = "listBriefingsByTopic";
    private static final String TOPIC = "topic";
    @Context
    UriInfo uriInfo;

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
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Topic name cannot be blank.").build();
        }
        User user = User.<User>findByIdOptional(userId).orElseThrow(() -> notFound("User", userId));
        Topic topic = new Topic();
        topic.user = user;
        topic.name = name;
        topic.persist();
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("/ui").build()).build();
    }

    @GET
    @Path("/{id}/edit")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showEditForm(@PathParam("id") Long id) {
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        return topicForm.data(TOPIC, topic);
    }

    @POST
    @Path("/update/{id}") // Changed path
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response updateTopic(@PathParam("id") Long id, @FormParam("name") String name) {
        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Topic name cannot be blank.").build();
        }
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        topic.name = name;
        topic.persist();
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("/ui").build()).build();
    }

    @POST
    @Path("/delete/{id}") // Changed path
    @Transactional
    public Response deleteTopic(@PathParam("id") Long id) {
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        topic.delete();
        return Response.seeOther(uriInfo.getBaseUriBuilder().path("/ui").build()).build();
    }

    @GET
    @Path("/{topicId}/briefings")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance listBriefingsByTopic(@PathParam("topicId") Long topicId) {
        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        Topic topic = query.firstResultOptional().orElseThrow(() -> topicNotFound(topicId));
        List<Briefing> briefings = Briefing.find("topic = ?1 order by briefingDate desc", topic).list();
        List<ContentSource> allContentSources = ContentSource.listAll();

        // Filter out already associated sources
        Set<Long> existingSourceIds = topic.contentSources.stream()
                .map(cs -> cs.id)
                .collect(Collectors.toSet());
        List<ContentSource> availableContentSources = allContentSources.stream()
                .filter(cs -> !existingSourceIds.contains(cs.id))
                .toList();

        return briefingsByTopic.data(TOPIC, topic)
                .data("briefings", briefings)
                .data("availableContentSources", availableContentSources); // Pass the filtered list
    }

    @POST
    @Path("/add-content-source/{topicId}") // Changed path
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addContentSourceToTopic(@PathParam("topicId") Long topicId, @FormParam("contentSourceId") Long contentSourceId) {
        if (contentSourceId == null) {
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(TopicResource.class).path(TopicResource.class, LIST_BRIEFINGS_BY_TOPIC).build(topicId)).build();
        }
        Topic topic = Topic.<Topic>findByIdOptional(topicId).orElseThrow(() -> topicNotFound(topicId));
        ContentSource contentSource = ContentSource.<ContentSource>findByIdOptional(contentSourceId).orElseThrow(() -> notFound("ContentSource", contentSourceId));
        topic.contentSources.add(contentSource);
        topic.persist();
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(TopicResource.class).path(TopicResource.class, LIST_BRIEFINGS_BY_TOPIC).build(topicId)).build();
    }

    @POST
    @Path("/remove-content-source/{topicId}/{contentSourceId}") // Changed path
    @Transactional
    public Response removeContentSourceFromTopic(@PathParam("topicId") Long topicId, @PathParam("contentSourceId") Long contentSourceId) {
        ContentSource contentSource = ContentSource.<ContentSource>findByIdOptional(contentSourceId).orElseThrow(() -> notFound("ContentSource", contentSourceId));
        Topic topic = Topic.<Topic>findByIdOptional(topicId).orElseThrow(() -> topicNotFound(topicId));
        topic.contentSources.remove(contentSource);
        topic.persist();
        return Response.seeOther(uriInfo.getBaseUriBuilder().path(TopicResource.class).path(TopicResource.class, LIST_BRIEFINGS_BY_TOPIC).build(topicId)).build();
    }

    private @NotNull NotFoundException topicNotFound(Long id) {
        return notFound("Topic", id);
    }

    private NotFoundException notFound(String entityName, Object id) {
        return new NotFoundException(String.format("%s with id %s not found.", entityName, id));
    }
}
