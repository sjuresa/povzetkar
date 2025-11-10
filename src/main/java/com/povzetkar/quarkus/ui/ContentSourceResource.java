package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.povzetkar.quarkus.ui.TopicResource.TOPICS_PATH;
import static com.povzetkar.quarkus.ui.TopicResource.UI_BASE_PATH;

@ApplicationScoped
public class ContentSourceResource {

    public static final String CONTENT_SOURCES_PATH = UI_BASE_PATH + "/content-sources";
    public static final String CONTENT_SOURCES_ADD_PATH = CONTENT_SOURCES_PATH + "/add";
    private static final String PARAM_TOPIC_ID = "topicId";
    private static final String INVALID_OR_MISSING_TOPIC_ID_PARAM = "Invalid or missing topicId parameter.";
    private static final String PARAM_SOURCE_TYPE_ID = "sourceTypeId";
    private static final String INVALID_SOURCE_TYPE_ID_FORMAT = "Invalid Source Type ID format.";
    private static final String INVALID_SOURCE_TYPE_ID = "Invalid Source Type ID.";
    private static final String TOPIC_ID_REQUIRED_MSG = "Topic ID is required to add a content source.";


    private final ViewUtils viewUtils;

    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm, ViewUtils viewUtils) {
        this.contentSourceForm = contentSourceForm;
        this.viewUtils = viewUtils;
    }

    private void renderAddFormWithData(RoutingContext context, Long topicId, String name, String sourceIdentifier, Integer sourceTypeId) {
        List<SourceTypeEnum> sourceTypes = Arrays.stream(SourceTypeEnum.values())
                .filter(st -> st != SourceTypeEnum.DEFAULT)
                .toList();

        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put(PARAM_TOPIC_ID, topicId);
        data.put("sourceTypes", sourceTypes);
        data.put("name", name);
        data.put("sourceIdentifier", sourceIdentifier);
        data.put(PARAM_SOURCE_TYPE_ID, sourceTypeId);
        data.put("breadcrumbs", List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb("Topics", TOPICS_PATH),
                new Breadcrumb("Add Content Source", context.request().uri())
        ));

        context.response().end(contentSourceForm.instance().data(data).render());
    }

    @Route(path = CONTENT_SOURCES_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        Optional<Long> topicIdOptional = WebUtils.getLongQueryParam(context, PARAM_TOPIC_ID, INVALID_OR_MISSING_TOPIC_ID_PARAM, msg -> context.response().setStatusCode(400).end(msg));
        if (topicIdOptional.isEmpty()) {
            return;
        }
        renderAddFormWithData(context, topicIdOptional.get(), null, null, null); // Fresh form
    }

    @Route(path = CONTENT_SOURCES_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSource(RoutingContext context) {
        String name = context.request().getFormAttribute("name");
        String sourceIdentifier = context.request().getFormAttribute("sourceIdentifier");
        Long topicId;

        try {
            topicId = WebUtils.getOptionalLongFormAttribute(context, PARAM_TOPIC_ID).orElse(null);
        } catch (IllegalArgumentException e) {
            viewUtils.setErrorMessage(context, String.format("Invalid format for topic ID: %s", e.getMessage()));
            renderAddFormWithData(context, null, name, sourceIdentifier, null); // Cannot determine topicId, so pass null
            return;
        }

        if (name == null || name.isBlank()) {
            viewUtils.setErrorMessage(context, "Name cannot be blank.");
            renderAddFormWithData(context, topicId, name, sourceIdentifier, null);
            return;
        }
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            viewUtils.setErrorMessage(context, "Source Identifier cannot be blank.");
            renderAddFormWithData(context, topicId, name, sourceIdentifier, null);
            return;
        }
        Optional<Integer> sourceTypeIdOptional = WebUtils.getIntegerFormAttribute(context, PARAM_SOURCE_TYPE_ID, INVALID_SOURCE_TYPE_ID_FORMAT, msg -> viewUtils.setErrorMessage(context, msg));
        if (sourceTypeIdOptional.isEmpty()) {
            renderAddFormWithData(context, topicId, name, sourceIdentifier, null);
            return;
        }
        int sourceTypeId = sourceTypeIdOptional.get();

        if (SourceTypeEnum.fromId(sourceTypeId) == SourceTypeEnum.DEFAULT) {
            viewUtils.setErrorMessage(context, INVALID_SOURCE_TYPE_ID);
            renderAddFormWithData(context, topicId, name, sourceIdentifier, sourceTypeId);
            return;
        }

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.persist();

        Optional<Long> topicIdOptionalFromForm = Optional.ofNullable(topicId);
        if (topicIdOptionalFromForm.isEmpty()) { // topicId is required for this UI flow
            viewUtils.setErrorMessage(context, TOPIC_ID_REQUIRED_MSG);
            renderAddFormWithData(context, null, name, sourceIdentifier, sourceTypeId);
            return;
        }

        Long currentTopicId = topicIdOptionalFromForm.get();
        Optional<Topic> topicOptional = Topic.findByIdOptional(currentTopicId);
        topicOptional.ifPresentOrElse(
                    topic -> {
                        topic.contentSources.add(contentSource);
                        topic.persist();
                        viewUtils.setSuccessMessage(context, "Content Source created and added to topic.");
                        viewUtils.handleRedirect(context, TOPICS_PATH + "/" + currentTopicId + "/briefings");
                    },
                    () -> {
                        viewUtils.setErrorMessage(context, String.format("Topic with id %d not found.", currentTopicId));
                        renderAddFormWithData(context, currentTopicId, name, sourceIdentifier, sourceTypeId);
                    }
            );
    }
}
