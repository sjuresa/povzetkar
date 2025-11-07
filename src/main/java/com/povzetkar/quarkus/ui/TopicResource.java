package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.NotFoundException;
import org.jetbrains.annotations.NotNull;

import java.util.List;

@ApplicationScoped
public class TopicResource {

    public static final String LIST_BRIEFINGS_BY_TOPIC = "listBriefingsByTopic";
    private static final String TOPIC = "topic";

    private final Template topicForm;
    private final Template briefingsByTopic;
    private final Template selectContentSource;

    public TopicResource(@Location("topic-form.html") Template topicForm, 
                         @Location("briefings-by-topic.html") Template briefingsByTopic,
                         @Location("select-content-source.html") Template selectContentSource) {
        this.topicForm = topicForm;
        this.briefingsByTopic = briefingsByTopic;
        this.selectContentSource = selectContentSource;
    }

    @Route(path = "/ui/topics/add", methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        List<User> users = User.listAll();
        context.response().end(topicForm.data("users", users).data(TOPIC, null).render());
    }

    @Route(path = "/ui/topics", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addTopic(RoutingContext context) {
        String userId = context.request().getFormAttribute("userId");
        String name = context.request().getFormAttribute("name");
        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Topic name cannot be blank.");
            return;
        }
        User user;
        try {
            user = User.<User>findByIdOptional(Long.parseLong(userId)).orElseThrow(() -> notFound("User", userId));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid userId format.");
            return;
        }
        Topic topic = new Topic();
        topic.user = user;
        topic.name = name;
        topic.persist();
        context.response().setStatusCode(303).putHeader("Location", "/ui").end();
    }

    @Route(path = "/ui/topics/:id/edit", methods = Route.HttpMethod.GET)
    @Blocking
    public void showEditForm(RoutingContext context) {
        Long id;
        try {
            id = Long.parseLong(context.pathParam("id"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid topic ID format.");
            return;
        }
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        context.response().end(topicForm.data(TOPIC, topic).render());
    }

    @Route(path = "/ui/topics/update/:id", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void updateTopic(RoutingContext context) {
        Long id;
        try {
            id = Long.parseLong(context.pathParam("id"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid topic ID format.");
            return;
        }
        String name = context.request().getFormAttribute("name");
        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Topic name cannot be blank.");
            return;
        }
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        topic.name = name;
        topic.persist();
        context.response().setStatusCode(303).putHeader("Location", "/ui").end();
    }

    @Route(path = "/ui/topics/delete/:id", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void deleteTopic(RoutingContext context) {
        Long id;
        try {
            id = Long.parseLong(context.pathParam("id"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid topic ID format.");
            return;
        }
        Topic topic = Topic.<Topic>findByIdOptional(id).orElseThrow(() -> topicNotFound(id));
        topic.delete();
        context.response().setStatusCode(303).putHeader("Location", "/ui").end();
    }

    @Route(path = "/ui/topics/:topicId/briefings", methods = Route.HttpMethod.GET)
    @Blocking
    public void listBriefingsByTopic(RoutingContext context) {
        Long topicId;
        try {
            topicId = Long.parseLong(context.pathParam("topicId"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid topic ID format.");
            return;
        }
        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        Topic topic = query.firstResultOptional().orElseThrow(() -> topicNotFound(topicId));
        List<Briefing> briefings = Briefing.find("topic = ?1 order by briefingDate desc", topic).list();

        context.response().end(briefingsByTopic.data(TOPIC, topic)
                .data("briefings", briefings).render());
    }

    @Route(path = "/ui/topics/:topicId/add-source/:contentSourceId", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSourceToTopic(RoutingContext context) {
        Long topicId;
        Long contentSourceId;
        try {
            topicId = Long.parseLong(context.pathParam("topicId"));
            contentSourceId = Long.parseLong(context.pathParam("contentSourceId"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid ID format in URL.");
            return;
        }
        Topic topic = Topic.<Topic>findByIdOptional(topicId).orElseThrow(() -> topicNotFound(topicId));
        ContentSource contentSource = ContentSource.<ContentSource>findByIdOptional(contentSourceId).orElseThrow(() -> notFound("ContentSource", contentSourceId));
        topic.contentSources.add(contentSource);
        contentSource.topics.add(topic);
        topic.persist();
        context.response().setStatusCode(303).putHeader("Location", "/ui/topics/" + topicId + "/briefings").end();
    }

    @Route(path = "/ui/topics/:topicId/remove-source/:contentSourceId", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void removeContentSourceFromTopic(RoutingContext context) {
        Long topicId;
        Long contentSourceId;
        try {
            topicId = Long.parseLong(context.pathParam("topicId"));
            contentSourceId = Long.parseLong(context.pathParam("contentSourceId"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid ID format in URL.");
            return;
        }
        ContentSource contentSource = ContentSource.<ContentSource>findByIdOptional(contentSourceId).orElseThrow(() -> notFound("ContentSource", contentSourceId));
        Topic topic = Topic.<Topic>findByIdOptional(topicId).orElseThrow(() -> topicNotFound(topicId));
        topic.contentSources.remove(contentSource);
        contentSource.topics.remove(topic);
        topic.persist();
        context.response().setStatusCode(303).putHeader("Location", "/ui/topics/" + topicId + "/briefings").end();
    }

    @Route(path = "/ui/topics/:topicId/select-source", methods = Route.HttpMethod.GET)
    @Blocking
    public void selectContentSource(RoutingContext context) {
        Long topicId;
        try {
            topicId = Long.parseLong(context.pathParam("topicId"));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end("Invalid topic ID format.");
            return;
        }
        String query = context.request().getParam("query");
        Topic topic = Topic.<Topic>findByIdOptional(topicId).orElseThrow(() -> topicNotFound(topicId));
        List<ContentSource> contentSources;
        if (query != null && !query.isBlank()) {
            contentSources = ContentSource.find("lower(name) like lower(?1)", "%" + query + "%").list();
        } else {
            contentSources = ContentSource.listAll();
        }
        context.response().end(selectContentSource.data(TOPIC, topic).data("contentSources", contentSources).data("query", query).render());
    }

    private @NotNull NotFoundException topicNotFound(Long id) {
        return notFound("Topic", id);
    }

    private NotFoundException notFound(String entityName, Object id) {
        return new NotFoundException(String.format("%s with id %s not found.", entityName, id));
    }
}
