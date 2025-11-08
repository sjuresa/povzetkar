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

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TopicResource {

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

    private Long getLongPathParam(RoutingContext context, String paramName, String errorMessage) {
        try {
            return Long.parseLong(context.pathParam(paramName));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
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
        String userIdStr = context.request().getFormAttribute("userId");
        String name = context.request().getFormAttribute("name");
        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Topic name cannot be blank.");
            return;
        }
        Long userId;
        try {
            userId = Long.parseLong(userIdStr);
        } catch (NumberFormatException | NullPointerException e) {
            context.response().setStatusCode(400).end("Invalid or missing userId format.");
            return;
        }

        Optional<User> userOptional = User.findByIdOptional(userId);
        userOptional.ifPresentOrElse(
                user -> {
                    Topic topic = new Topic();
                    topic.user = user;
                    topic.name = name;
                    topic.persist();
                    context.response().setStatusCode(303).putHeader("Location", "/ui").end();
                },
                () -> context.response().setStatusCode(404).end(String.format("User with id %d not found.", userId))
        );
    }

    @Route(path = "/ui/topics/:id/edit", methods = Route.HttpMethod.GET)
    @Blocking
    public void showEditForm(RoutingContext context) {
        Long id = getLongPathParam(context, "id", "Invalid topic ID format.");
        if (id == null) {
            return;
        }
        Topic.findByIdOptional(id).ifPresentOrElse(
                topic -> context.response().end(topicForm.data(TOPIC, topic).render()),
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", id))
        );
    }

    @Route(path = "/ui/topics/update/:id", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void updateTopic(RoutingContext context) {
        Long id = getLongPathParam(context, "id", "Invalid topic ID format.");
        if (id == null) {
            return;
        }
        String name = context.request().getFormAttribute("name");
        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Topic name cannot be blank.");
            return;
        }
        Topic.<Topic>findByIdOptional(id).ifPresentOrElse(
                topic -> {
                    topic.name = name;
                    topic.persist();
                    context.response().setStatusCode(303).putHeader("Location", "/ui").end();
                },
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", id))
        );
    }

    @Route(path = "/ui/topics/delete/:id", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void deleteTopic(RoutingContext context) {
        Long id = getLongPathParam(context, "id", "Invalid topic ID format.");
        if (id == null) {
            return;
        }
        Topic.<Topic>findByIdOptional(id).ifPresentOrElse(
                topic -> {
                    topic.delete();
                    context.response().setStatusCode(303).putHeader("Location", "/ui").end();
                },
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", id))
        );
    }

    @Route(path = "/ui/topics/:topicId/briefings", methods = Route.HttpMethod.GET)
    @Blocking
    public void listBriefingsByTopic(RoutingContext context) {
        Long topicId = getLongPathParam(context, "topicId", "Invalid topic ID format.");
        if (topicId == null) {
            return;
        }
        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        query.firstResultOptional().ifPresentOrElse(
                topic -> {
                    List<Briefing> briefings = Briefing.find("topic = ?1 order by briefingDate desc", topic).list();
                    context.response()
                            .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                            .end(briefingsByTopic.data(TOPIC, topic)
                                    .data("briefings", briefings).render());
                },
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", topicId))
        );
    }

    @Route(path = "/ui/topics/:topicId/add-source/:contentSourceId", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSourceToTopic(RoutingContext context) {
        Long topicId = getLongPathParam(context, "topicId", "Invalid topic ID format.");
        if (topicId == null) {
            return;
        }
        Long contentSourceId = getLongPathParam(context, "contentSourceId", "Invalid content source ID format.");
        if (contentSourceId == null) {
            return;
        }
        Topic.<Topic>findByIdOptional(topicId).ifPresentOrElse(
                topic -> ContentSource.<ContentSource>findByIdOptional(contentSourceId).ifPresentOrElse(
                        contentSource -> {
                            topic.contentSources.add(contentSource);
                            contentSource.topics.add(topic);
                            topic.persist();
                            context.response().setStatusCode(303).putHeader("Location", "/ui/topics/" + topicId + "/briefings").end();
                        },
                        () -> context.response().setStatusCode(404).end(String.format("ContentSource with id %d not found.", contentSourceId))
                ),
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", topicId))
        );
    }

    @Route(path = "/ui/topics/:topicId/remove-source/:contentSourceId", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void removeContentSourceFromTopic(RoutingContext context) {
        Long topicId = getLongPathParam(context, "topicId", "Invalid topic ID format.");
        if (topicId == null) {
            return;
        }
        Long contentSourceId = getLongPathParam(context, "contentSourceId", "Invalid content source ID format.");
        if (contentSourceId == null) {
            return;
        }
        Topic.<Topic>findByIdOptional(topicId).ifPresentOrElse(
                topic -> ContentSource.<ContentSource>findByIdOptional(contentSourceId).ifPresentOrElse(
                        contentSource -> {
                            topic.contentSources.remove(contentSource);
                            contentSource.topics.remove(topic);
                            topic.persist();
                            context.response().setStatusCode(303).putHeader("Location", "/ui/topics/" + topicId + "/briefings").end();
                        },
                        () -> context.response().setStatusCode(404).end(String.format("ContentSource with id %d not found.", contentSourceId))
                ),
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", topicId))
        );
    }

    @Route(path = "/ui/topics/:topicId/select-source", methods = Route.HttpMethod.GET)
    @Blocking
    public void selectContentSource(RoutingContext context) {
        Long topicId = getLongPathParam(context, "topicId", "Invalid topic ID format.");
        if (topicId == null) {
            return;
        }
        String query = context.request().getParam("query");
        Topic.<Topic>findByIdOptional(topicId).ifPresentOrElse(
                topic -> {
                    List<ContentSource> contentSources;
                    if (query != null && !query.isBlank()) {
                        contentSources = ContentSource.find("lower(name) like lower(?1)", "%" + query + "%").list();
                    } else {
                        contentSources = ContentSource.listAll();
                    }
                    context.response().end(selectContentSource.data(TOPIC, topic).data("contentSources", contentSources).data("query", query).render());
                },
                () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", topicId))
        );
    }
}
