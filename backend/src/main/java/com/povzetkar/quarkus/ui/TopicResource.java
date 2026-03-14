package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.FormValidator;
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
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class TopicResource {
    private static final Logger logger = LoggerFactory.getLogger(TopicResource.class);

    public static final String UI_BASE_PATH = "/ui";
    public static final String TOPICS_PATH = UI_BASE_PATH + "/topics"; // /ui/topics
    public static final String TOPIC_ADD_PATH = TOPICS_PATH + "/add"; // /ui/topics/add
    public static final String TOPIC_EDIT_PATH = TOPICS_PATH + "/:id/edit"; // /ui/topics/:id/edit
    public static final String TOPIC_UPDATE_PATH = TOPICS_PATH + "/update/:id"; // /ui/topics/update/:id
    public static final String TOPIC_DELETE_PATH = TOPICS_PATH + "/delete/:id"; // /ui/topics/delete/:id
    public static final String TOPIC_TOGGLE_ENABLED_PATH = TOPICS_PATH + "/:id/toggle-enabled"; // /ui/topics/:id/toggle-enabled
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
    static final String TOPIC_NOT_FOUND_MSG = "Topic with id %d not found.";
    private static final String PARAM_CONTENT_SOURCE_ID = "contentSourceId";
    private static final String INVALID_CONTENT_SOURCE_ID_FORMAT = "Invalid content source ID format.";
    private static final String CONTENT_SOURCE_NOT_FOUND_MSG = "ContentSource with id %d not found.";

    private final ViewUtils viewUtils;
    private final UserContextService userContextService;
    private final Template topics;
    private final Template topicForm;
    private final Template briefingsByTopic;
    private final Template selectContentSource;

    @ConfigProperty(name = "povzetkar.ui.date.format")
    String dateFormat;

    public TopicResource(@Location("topics.html") Template topics,
                         @Location("topic-form.html") Template topicForm,
                         @Location("briefings-by-topic.html") Template briefingsByTopic,
                         @Location("select-content-source.html") Template selectContentSource,
                         ViewUtils viewUtils,
                         UserContextService userContextService) {
        this.topics = topics;
        this.topicForm = topicForm;
        this.briefingsByTopic = briefingsByTopic;
        this.selectContentSource = selectContentSource;
        this.viewUtils = viewUtils;
        this.userContextService = userContextService;
    }

    public static String topicBriefingsPath(long topicId) {
        return TOPIC_BRIEFINGS_PATH.replace(TOPIC_ID_PATH_PARAM, String.valueOf(topicId));
    }

    // DTO for form data to avoid using dummy entities
    public record TopicFormData(Long id, String name) {}

    private void renderAddForm(RoutingContext context, String name) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        
        TopicFormData formData = new TopicFormData(
            null, 
            name
        );
        
        data.put(TOPIC_KEY, formData);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                new Breadcrumb("Add Topic", TOPIC_ADD_PATH)
        ));
        context.response().end(topicForm.instance().data(data).render());
    }

    private void renderEditForm(RoutingContext context, TopicFormData formData) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put(TOPIC_KEY, formData);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH),
                new Breadcrumb(formData.name(), topicBriefingsPath(formData.id())),
                new Breadcrumb("Edit", context.request().uri())
        ));
        context.response().end(topicForm.instance().data(data).render());
    }

    @Route(path = TOPICS_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getTopics(RoutingContext context) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        Optional<User> currentUserOpt = userContextService.getCurrentUser();

        Map<User, List<Topic>> topicsByUser;
        if (currentUserOpt.isEmpty()) {
            topicsByUser = Collections.emptyMap();
        } else {
            User currentUser = currentUserOpt.get();
            List<Topic> userTopics = Topic
                    .find("from Topic t left join fetch t.contentSources where t.user = ?1", currentUser).list();
            topicsByUser = Map.of(currentUser, userTopics.stream()
                    .sorted(Comparator.comparing(topic -> topic.name))
                    .toList());
        }

        data.put("topicsByUser", topicsByUser);
        data.put(BREADCRUMBS_KEY, List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb(TOPICS_LABEL, TOPICS_PATH)
        ));
        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .end(topics.instance().data(data).render());
    }

    @Route(path = TOPIC_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        renderAddForm(context, null); // Fresh form
    }

    @Route(path = TOPICS_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addTopic(RoutingContext context) {
        FormValidator validator = new FormValidator();
        String name = context.request().getFormAttribute("name");

        validator.validateNotBlank(name, "Topic name cannot be blank.");

        Optional<User> currentUserOpt = userContextService.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            viewUtils.setErrorMessage(context, "User not found.");
            renderAddForm(context, name);
            return;
        }

        if (validator.hasErrors()) {
            viewUtils.setErrorMessage(context, String.join("; ", validator.getErrors()));
            renderAddForm(context, name); // Re-render with submitted values
            return;
        }

        User currentUser = currentUserOpt.get();
        Topic topic = new Topic();
        topic.user = currentUser;
        topic.name = name;
        logger.info("Persisting topic: {} for user: {}", name, currentUser.username);
        topic.persist();
        logger.info("Topic persisted with ID: {}", topic.id);
        viewUtils.setSuccessMessage(context, "Topic created successfully.");
        viewUtils.handleRedirect(context, TOPICS_PATH);
    }

    @Route(path = TOPIC_EDIT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, schema = @Schema(type = SchemaType.INTEGER))
    public void showEditForm(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();
        userContextService.findOwnedTopic(id).ifPresentOrElse(
                topic -> {
                    TopicFormData formData = new TopicFormData(
                        topic.id, 
                        topic.name
                    );
                    renderEditForm(context, formData);
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_UPDATE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    public void updateTopic(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();

        Optional<Topic> topicOptional = userContextService.findOwnedTopic(id);
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
            // Create DTO with submitted name but existing ID
            TopicFormData formData = new TopicFormData(
                topic.id, 
                name
            );
            renderEditForm(context, formData);
            return;
        }

        topic.name = name;
        topic.persist();
        viewUtils.setSuccessMessage(context, "Topic updated successfully.");
        viewUtils.handleRedirect(context, topicBriefingsPath(id));
    }

    @Route(path = TOPIC_DELETE_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    public void deleteTopic(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();
        userContextService.findOwnedTopic(id).ifPresentOrElse(topic -> {
                    String topicName = topic.name;
                    topic.delete();
                    viewUtils.setSuccessMessage(context, "Topic '" + topicName + "' deleted successfully.");
                    viewUtils.handleRedirect(context, TOPICS_PATH);
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_TOGGLE_ENABLED_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    public void toggleTopicEnabled(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, PARAM_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (idOptional.isEmpty()) {
            return;
        }
        Long id = idOptional.get();
        userContextService.findOwnedTopic(id).ifPresentOrElse(topic -> {
                    topic.enabled = !topic.enabled;
                    topic.persist();
                    String status = topic.enabled ? "enabled" : "disabled";
                    viewUtils.setSuccessMessage(context, "Topic '" + topic.name + "' " + status + " successfully.");
                    viewUtils.handleRedirect(context, TOPICS_PATH);
                },
                () -> context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, id))
        );
    }

    @Route(path = TOPIC_BRIEFINGS_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    @Parameter(name = "topicId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    public void listBriefingsByTopic(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return;
        }
        Long topicId = topicIdOptional.get();
        
        Optional<User> currentUserOpt = userContextService.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
             context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId));
             return;
        }
        User currentUser = currentUserOpt.get();

        PanacheQuery<Topic> query = Topic.find("select t from Topic t left join fetch t.contentSources where t.id = ?1 and t.user = ?2", topicId, currentUser);
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
                            .putHeader("Content-Type", "text/html; charset=utf-8")
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

        Optional<Long> contentSourceIdOptional = WebUtils.getLongPathParam(context, PARAM_CONTENT_SOURCE_ID,
                INVALID_CONTENT_SOURCE_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (contentSourceIdOptional.isEmpty()) {
            return Optional.empty();
        }
        Long contentSourceId = contentSourceIdOptional.get();

        Optional<Topic> topicOptional = userContextService.findOwnedTopic(topicId);
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
    @Parameter(name = "topicId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    @Parameter(name = "contentSourceId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
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
    @Parameter(name = "topicId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    @Parameter(name = "contentSourceId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
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
    @Parameter(name = "topicId", in = ParameterIn.PATH, required = true, schema = @Schema(type = org.eclipse.microprofile.openapi.annotations.enums.SchemaType.INTEGER))
    public void selectContentSource(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongPathParam(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return;
        }
        Long topicId = topicIdOptional.get();
        
        Optional<User> currentUserOpt = userContextService.getCurrentUser();
        if (currentUserOpt.isEmpty()) {
             context.response().setStatusCode(404).end(String.format(TOPIC_NOT_FOUND_MSG, topicId));
             return;
        }
        User currentUser = currentUserOpt.get();

        // Use a custom query to fetch topic with content sources AND verify ownership
        Topic.find("select t from Topic t left join fetch t.contentSources where t.id = ?1 and t.user = ?2", topicId, currentUser)
             .firstResultOptional()
             .map(t -> (Topic) t)
             .ifPresentOrElse(topic -> {
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
