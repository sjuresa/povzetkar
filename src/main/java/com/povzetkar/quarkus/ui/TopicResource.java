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

    // UI Paths
    public static final String UI_BASE_PATH = "/ui";
    public static final String TOPICS_PATH = UI_BASE_PATH + "/topics";
    public static final String TOPIC_ADD_PATH = TOPICS_PATH + "/add";
    public static final String TOPIC_EDIT_PATH = TOPICS_PATH + "/:id/edit";
    public static final String TOPIC_UPDATE_PATH = TOPICS_PATH + "/update/:id";
    public static final String TOPIC_DELETE_PATH = TOPICS_PATH + "/delete/:id";
    public static final String TOPIC_BRIEFINGS_PATH = TOPICS_PATH + "/:topicId/briefings";
    public static final String TOPIC_ADD_SOURCE_PATH = TOPICS_PATH + "/:topicId/add-source/:contentSourceId";
    public static final String TOPIC_REMOVE_SOURCE_PATH = TOPICS_PATH + "/:topicId/remove-source/:contentSourceId";
    public static final String TOPIC_SELECT_SOURCE_PATH = TOPICS_PATH + "/:topicId/select-source";

    // Template and Message Constants
    private static final String TOPIC = "topic";
    private static final String HEADER_LOCATION = "Location";
    private static final String INVALID_TOPIC_ID_FORMAT = "Invalid topic ID format.";
    private static final String TOPIC_NOT_FOUND_MSG = "Topic with id %d not found.";
    private static final String PARAM_TOPIC_ID = "topicId";
    private static final String PARAM_CONTENT_SOURCE_ID = "contentSourceId";
    private static final String INVALID_CONTENT_SOURCE_ID_FORMAT = "Invalid content source ID format.";
    private static final String CONTENT_SOURCE_NOT_FOUND_MSG = "ContentSource with id %d not found.";


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

    public static Long getLongPathParam(RoutingContext context, String paramName, String errorMessage) {
        try {
            return Long.parseLong(context.pathParam(paramName));
        } catch (NumberFormatException e) {
            context.response().setStatusCode(400).end(errorMessage);
            return null;
        }
    }

    @Route(path = TOPIC_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        List<User> users = User.listAll();
        context.response().end(topicForm.data("users", users).data(TOPIC, null).render());
    }

    @Route(path = TOPICS_PATH, methods = Route.HttpMethod.POST)
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
                    context.response().setStatusCode(303).putHeader(HEADER_LOCATION, UI_BASE_PATH).end();
                },
                () -> context.response().setStatusCode(404).end(String.format("User with id %d not found.", userId))
        );
    }

    @Route(path = TOPIC_EDIT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showEditForm(RoutingContext context) {
        Long id = getLongPathParam(context, "id", INVALID_TOPIC_ID_FORMAT);
        if (id == null) {
            return;
        }
        Topic.findByIdOptional(id).ifPresentOrElse(
                topic -> context.response().end(topicForm.data(TOPIC, topic).render()),
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_UPDATE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void updateTopic(RoutingContext context) {
        Long id = getLongPathParam(context, "id", INVALID_TOPIC_ID_FORMAT);
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
                    context.response().setStatusCode(303).putHeader(HEADER_LOCATION, UI_BASE_PATH).end();
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_DELETE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void deleteTopic(RoutingContext context) {
        Long id = getLongPathParam(context, "id", INVALID_TOPIC_ID_FORMAT);
        if (id == null) {
            return;
        }
        Topic.<Topic>findByIdOptional(id).ifPresentOrElse(
                topic -> {
                    topic.delete();
                    context.response().setStatusCode(303).putHeader(HEADER_LOCATION, UI_BASE_PATH).end();
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_BRIEFINGS_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void listBriefingsByTopic(RoutingContext context) {
        Long topicId = getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT);
        if (topicId == null) {
            return;
        }
        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        query.firstResultOptional().ifPresentOrElse(
                topic -> {
                    List<Briefing> briefings = Briefing.find("topic = ?1 order by briefingDate desc", topic).list();
                    context.response()
                            .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                            .putHeader("Pragma", "no-cache")
                            .end(briefingsByTopic.data(TOPIC, topic)
                                    .data("briefings", briefings).render());
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId))
        );
    }

    private record TopicAndContentSource(Topic topic, ContentSource contentSource) {
    }

    private Optional<TopicAndContentSource> findTopicAndContentSource(RoutingContext context) {
        Long topicId = getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT);
        if (topicId == null) {
            return Optional.empty();
        }
        Long contentSourceId = getLongPathParam(context, PARAM_CONTENT_SOURCE_ID, INVALID_CONTENT_SOURCE_ID_FORMAT);
        if (contentSourceId == null) {
            return Optional.empty();
        }

        Optional<Topic> topicOptional = Topic.findByIdOptional(topicId);
        if (topicOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId));
            return Optional.empty();
        }

        Optional<ContentSource> contentSourceOptional = ContentSource.findByIdOptional(contentSourceId);
        if (contentSourceOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(CONTENT_SOURCE_NOT_FOUND_MSG, contentSourceId));
            return Optional.empty();
        }

        return Optional.of(new TopicAndContentSource(topicOptional.get(), contentSourceOptional.get()));
    }

    @Route(path = TOPIC_ADD_SOURCE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSourceToTopic(RoutingContext context) {
        findTopicAndContentSource(context).ifPresent(pair -> {
            pair.topic.contentSources.add(pair.contentSource);
            pair.contentSource.topics.add(pair.topic);
            pair.topic.persist();
            context.response().setStatusCode(303).putHeader(HEADER_LOCATION, TOPICS_PATH + "/" + pair.topic.id + "/briefings").end();
        });
    }

    @Route(path = TOPIC_REMOVE_SOURCE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void removeContentSourceFromTopic(RoutingContext context) {
        findTopicAndContentSource(context).ifPresent(pair -> {
            pair.topic.contentSources.remove(pair.contentSource);
            pair.contentSource.topics.remove(pair.topic);
            pair.topic.persist();
            context.response().setStatusCode(303).putHeader(HEADER_LOCATION, TOPICS_PATH + "/" + pair.topic.id + "/briefings").end();
        });
    }

    @Route(path = TOPIC_SELECT_SOURCE_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void selectContentSource(RoutingContext context) {
        Long topicId = getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT);
        if (topicId == null) {
            return;
        }
        String query = context.request().getParam("query");
        Topic.<Topic>findByIdOptional(topicId).ifPresentOrElse(
                topic -> {
                    List<ContentSource> contentSources;
                    if (query != null && !query.isBlank()) {
                        String sanitizedQuery = query.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
                        contentSources = ContentSource.find("lower(name) like lower(?1)", "%" + sanitizedQuery + "%").list();
                    } else {
                        contentSources = ContentSource.listAll();
                    }
                    context.response().end(selectContentSource.data(TOPIC, topic).data("contentSources", contentSources).data("query", query).render());
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId))
        );
    }
}
