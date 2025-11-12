package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.ui.util.FormValidator;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TopicResource {

    public static final String UI_BASE_PATH = "/ui";
    public static final String TOPICS_PATH = UI_BASE_PATH + "/topics"; // /ui/topics
    public static final String TOPIC_ADD_PATH = TOPICS_PATH + "/add"; // /ui/topics/add
    public static final String TOPIC_EDIT_PATH = TOPICS_PATH + "/:id/edit"; // /ui/topics/:id/edit
    public static final String TOPIC_UPDATE_PATH = TOPICS_PATH + "/update/:id"; // /ui/topics/update/:id
    public static final String TOPIC_DELETE_PATH = TOPICS_PATH + "/delete/:id"; // /ui/topics/delete/:id
    public static final String TOPIC_BRIEFINGS_PATH = TOPICS_PATH + "/:topicId/briefings";
    public static final String TOPIC_ADD_SOURCE_PATH = TOPICS_PATH + "/:topicId/add-source/:contentSourceId";
    public static final String TOPIC_REMOVE_SOURCE_PATH = TOPICS_PATH + "/:topicId/remove-source/:contentSourceId";
    public static final String TOPIC_SELECT_SOURCE_PATH = TOPICS_PATH + "/:topicId/select-source";

    private static final String TOPIC_KEY = "topic";
    private static final String BREADCRUMBS_KEY = "breadcrumbs";
    private static final String TOPICS_LABEL = "Topics";
    private static final String PARAM_ID = "id";
    private static final String PARAM_TOPIC_ID = "topicId";
    public static final String TOPIC_ID_PATH_PARAM = ":topicId";

    private static final String INVALID_TOPIC_ID_FORMAT = "Invalid topic ID format.";
    private static final String TOPIC_NOT_FOUND_MSG = "Topic with id %d not found.";
    private static final String PARAM_CONTENT_SOURCE_ID = "contentSourceId";
    private static final String INVALID_CONTENT_SOURCE_ID_FORMAT = "Invalid content source ID format.";
    private static final String CONTENT_SOURCE_NOT_FOUND_MSG = "ContentSource with id %d not found.";
    private static final String PARAM_USER_ID = "userId";
    private static final String INVALID_USER_ID_FORMAT = "Invalid userId format.";

    private final ViewUtils viewUtils;

    @ConfigProperty(name = "povzetkar.ui.date.format")
    String dateFormat;

    private final Template topics;
    private final Template topicForm;
    private final Template briefingsByTopic;
    private final Template selectContentSource;

    public TopicResource(@Location("topics.html") Template topics,
                         @Location("topic-form.html") Template topicForm,
                         @Location("briefings-by-topic.html") Template briefingsByTopic,
                         @Location("select-content-source.html") Template selectContentSource,
                         ViewUtils viewUtils) {
        this.topics = topics;
        this.topicForm = topicForm;
        this.briefingsByTopic = briefingsByTopic;
        this.selectContentSource = selectContentSource;
        this.viewUtils = viewUtils;
    }

    public static String topicBriefingsPath(long topicId) {
        return TOPIC_BRIEFINGS_PATH.replace(TOPIC_ID_PATH_PARAM, String.valueOf(topicId));
    }

    private void renderAddForm(RoutingContext context, String name, Long userId) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("users", User.listAll());
        // Create a dummy topic to hold the submitted name for re-population
        Topic submittedTopic = new Topic();
        submittedTopic.name = name;
        User dummyUser = new User();
        dummyUser.id = userId;
        submittedTopic.user = dummyUser;
        data.put(TOPIC_KEY, submittedTopic);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                new Breadcrumb("Add Topic", TOPIC_ADD_PATH)
        ));
        context.response().end(topicForm.instance().data(data).render());
    }

    private void renderEditForm(RoutingContext context, Topic topic) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put(TOPIC_KEY, topic);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                new Breadcrumb(topic.name, topicBriefingsPath(topic.id)),
                new Breadcrumb("Edit", context.request().uri())
        ));
        context.response().end(topicForm.instance().data(data).render());
    }

    @Route(path = TOPICS_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getTopics(RoutingContext context) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        List<User> usersWithTopics = User.findAllWithTopics();
        Map<User, List<Topic>> topicsByUser = usersWithTopics.stream()
                .filter(user -> !user.topics.isEmpty())
                .collect(Collectors.toMap(user -> user, user -> user.topics.stream()
                        .sorted(Comparator.comparing(topic -> topic.name))
                        .toList()));
        data.put("topicsByUser", topicsByUser);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH)
        ));
        context.response()
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .end(topics.instance().data(data).render());
    }

    @Route(path = TOPIC_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        renderAddForm(context, null, null); // Fresh form
    }

    @Route(path = TOPICS_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addTopic(RoutingContext context) {
        FormValidator validator = new FormValidator();
        String name = context.request().getFormAttribute("name");
        Optional<Long> userIdOptional = WebUtils.getLongFormAttribute(context, PARAM_USER_ID, INVALID_USER_ID_FORMAT, validator::addError);
        Long userId = userIdOptional.orElse(null); // Get userId or null if validation failed

        validator.validateNotBlank(name, "Topic name cannot be blank.");
        validator.validateNotNull(userId, "User is required.");

        if (validator.hasErrors()) {
            viewUtils.setErrorMessage(context, String.join("; ", validator.getErrors()));
            renderAddForm(context, name, userId); // Re-render with submitted values
            return;
        }

        // Validation passed, it's safe to get the user ID
        Optional<User> userOptional = User.findByIdOptional(userId);
        userOptional.ifPresentOrElse(
                user -> {
                    Topic topic = new Topic();
                    topic.user = user;
                    topic.name = name;
                    topic.persist();
                    viewUtils.setSuccessMessage(context, "Topic created successfully.");
                    viewUtils.handleRedirect(context, TOPICS_PATH);
                },
                () -> {
                    viewUtils.setErrorMessage(context, String.format("User with id %d not found.", userId));
                    renderAddForm(context, name, userId); // Re-render with submitted values
                }
        );
    }

    @Route(path = TOPIC_EDIT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showEditForm(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();
        Topic.findByIdOptional(id).ifPresentOrElse(
            topic -> renderEditForm(context, (Topic) topic),
            () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_UPDATE_PATH, methods = Route.HttpMethod.POST)
    @Blocking
    public void updateTopic(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();

        Optional<Topic> topicOptional = Topic.findByIdOptional(id);
        if (topicOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id));
            return;
        }
        Topic topic = topicOptional.get();

        FormValidator validator = new FormValidator();
        String name = context.request().getFormAttribute("name");
        validator.validateNotBlank(name, "Topic name cannot be blank.");

        if (validator.hasErrors()) {
            viewUtils.setErrorMessage(context, String.join("; ", validator.getErrors()));
            topic.name = name; // Set the invalid name for re-rendering
            renderEditForm(context, topic);
            return;
        }

        persistTopicUpdate(topic, name);
        viewUtils.setSuccessMessage(context, "Topic updated successfully.");
        viewUtils.handleRedirect(context, topicBriefingsPath(id));
    }

    @Transactional
    void persistTopicUpdate(Topic topic, String name) {
        topic.name = name;
        topic.persist();
    }

    @Transactional
    void persistTopicUpdate(Long id, String name) {
        Topic.findByIdOptional(id).ifPresentOrElse(topic -> {
            ((Topic) topic).name = name;
            topic.persist();
        }, () -> {
            // This case should ideally not be reached if validation is done before calling
            throw new IllegalStateException(String.format(TOPIC_NOT_FOUND_MSG, id));
        });
    }

    @Route(path = TOPIC_DELETE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void deleteTopic(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();
        Topic.findByIdOptional(id).ifPresentOrElse(topic -> {
                    String topicName = ((Topic)topic).name;
                    topic.delete();
                    viewUtils.setSuccessMessage(context, "Topic '" + topicName + "' deleted successfully.");
                    viewUtils.handleRedirect(context, TOPICS_PATH);
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_BRIEFINGS_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void listBriefingsByTopic(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return;
        }
        Long topicId = topicIdOptional.get();

        PanacheQuery<Topic> query = Topic.find("from Topic t left join fetch t.contentSources where t.id = ?1", topicId);
        query.firstResultOptional().ifPresentOrElse(
                topic -> {
                    Map<String, Object> data = viewUtils.createTemplateData(context);
                    data.put(TOPIC_KEY, topic);
                    data.put("briefings", Briefing.find("topic = ?1 order by briefingDate desc", topic).list());
                    data.put("dateFormat", dateFormat);
                    data.put(BREADCRUMBS_KEY, List.of(
                            ViewUtils.HOME_BREADCRUMB,
                            new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                            new Breadcrumb(topic.name, context.request().uri())
                    ));
                    context.response()
                            .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                            .putHeader("Pragma", "no-cache")
                            .end(briefingsByTopic.instance().data(data).render());
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId))
        );
    }

    private record TopicAndContentSource(Topic topic, ContentSource contentSource) {}

    private Optional<TopicAndContentSource> findTopicAndContentSource(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return Optional.empty();
        }
        Long topicId = topicIdOptional.get();

        Optional<Long> contentSourceIdOptional = WebUtils.getLongPathParam(context, PARAM_CONTENT_SOURCE_ID, INVALID_CONTENT_SOURCE_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (contentSourceIdOptional.isEmpty()) {
            return Optional.empty();
        }
        Long contentSourceId = contentSourceIdOptional.get();

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
            viewUtils.setSuccessMessage(context, "Content source added to topic.");
            viewUtils.handleRedirect(context, TOPIC_SELECT_SOURCE_PATH.replace(TOPIC_ID_PATH_PARAM, String.valueOf(pair.topic.id)));
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
            viewUtils.setSuccessMessage(context, "Content source removed from topic.");
            viewUtils.handleRedirect(context, topicBriefingsPath(pair.topic.id));
        });
    }

    @Route(path = TOPIC_SELECT_SOURCE_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void selectContentSource(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return;
        }
        Long topicId = topicIdOptional.get();
        Topic.findByIdWithContentSources(topicId).ifPresentOrElse(topic -> { // Use the new method
            Map<String, Object> data = viewUtils.createTemplateData(context);
            String query = context.request().getParam("query");
            List<ContentSource> contentSources;
            if (query != null && !query.isBlank()) {
                contentSources = ContentSource.find("lower(name) like lower(?1)", "%" + query + "%").list();
            } else {
                contentSources = ContentSource.listAll();
            }

            // Filter out content sources already associated with the topic
            Set<Long> existingSourceIds = topic.contentSources.stream()
                                            .map(cs -> cs.id)
                                            .collect(Collectors.toSet());
            List<ContentSource> filteredContentSources = contentSources.stream()
                                                        .filter(cs -> !existingSourceIds.contains(cs.id))
                                                        .toList();

            data.put(TOPIC_KEY, topic);
            data.put("contentSources", filteredContentSources); // Pass the filtered list
            data.put("query", query);
            data.put(BREADCRUMBS_KEY, List.of(
                    ViewUtils.HOME_BREADCRUMB,
                    new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                    new Breadcrumb((topic).name, topicBriefingsPath((topic).id)),
                    new Breadcrumb("Select Source", context.request().uri())
            ));
            context.response().end(selectContentSource.instance().data(data).render());
        },
        () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId))
        );
    }
}
