package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
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
import static com.povzetkar.quarkus.ui.util.WebUtils.*;

@ApplicationScoped
public class ContentSourceResource {

    public static final String CONTENT_SOURCES_PATH = UI_BASE_PATH + "/content-sources";
    public static final String CONTENT_SOURCES_ADD_PATH = CONTENT_SOURCES_PATH + "/add";
    private static final String PARAM_TOPIC_ID = "topicId";
    private static final String INVALID_OR_MISSING_TOPIC_ID_PARAM = "Invalid or missing topicId parameter.";
    private static final String INVALID_TOPIC_ID_FORMAT = "Invalid topic ID format.";
    private static final String PARAM_SOURCE_TYPE_ID = "sourceTypeId";
    private static final String INVALID_SOURCE_TYPE_ID_FORMAT = "Invalid Source Type ID format.";
    private static final String INVALID_SOURCE_TYPE_ID = "Invalid Source Type ID.";

    private final ViewUtils viewUtils;

    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm, ViewUtils viewUtils) {
        this.contentSourceForm = contentSourceForm;
        this.viewUtils = viewUtils;
    }

    @Route(path = CONTENT_SOURCES_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        Long topicId = getLongQueryParam(context, PARAM_TOPIC_ID, INVALID_OR_MISSING_TOPIC_ID_PARAM);
        if (topicId == null) {
            return;
        }
        List<SourceTypeEnum> sourceTypes = Arrays.stream(SourceTypeEnum.values())
                .filter(st -> st != SourceTypeEnum.DEFAULT)
                .toList();

        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put(PARAM_TOPIC_ID, topicId);
        data.put("sourceTypes", sourceTypes);
        data.put("breadcrumbs", List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb("Topics", TOPICS_PATH),
                new Breadcrumb("Add Content Source", context.request().uri())
        ));

        context.response().end(contentSourceForm.instance().data(data).render());
    }

    @Route(path = CONTENT_SOURCES_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSource(RoutingContext context) {
        String name = context.request().getFormAttribute("name");
        String sourceIdentifier = context.request().getFormAttribute("sourceIdentifier");

        if (name == null || name.isBlank()) {
            viewUtils.setErrorMessage(context, "Name cannot be blank.");
            showAddForm(context);
            return;
        }
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            viewUtils.setErrorMessage(context, "Source Identifier cannot be blank.");
            showAddForm(context);
            return;
        }
        int sourceTypeId;
        String sourceTypeIdStr = context.request().getFormAttribute(PARAM_SOURCE_TYPE_ID);
        if (sourceTypeIdStr == null || sourceTypeIdStr.isBlank()) {
            viewUtils.setErrorMessage(context, String.format("Form attribute '%s' is required.", PARAM_SOURCE_TYPE_ID));
            showAddForm(context);
            return;
        }
        try {
            sourceTypeId = Integer.parseInt(sourceTypeIdStr);
        } catch (NumberFormatException e) {
            viewUtils.setErrorMessage(context, INVALID_SOURCE_TYPE_ID_FORMAT);
            showAddForm(context);
            return;
        }
        if (SourceTypeEnum.fromId(sourceTypeId) == SourceTypeEnum.DEFAULT) {
            viewUtils.setErrorMessage(context, INVALID_SOURCE_TYPE_ID);
            showAddForm(context);
            return;
        }

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.persist();

        Optional<Long> topicIdOptional = getOptionalLongFormAttribute(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT);
        if (topicIdOptional.isPresent()) {
            Long topicId = topicIdOptional.get();
            Optional<Topic> topicOptional = Topic.findByIdOptional(topicId);
            topicOptional.ifPresentOrElse(
                    topic -> {
                        topic.contentSources.add(contentSource);
                        topic.persist();
                        viewUtils.setSuccessMessage(context, "Content Source created and added to topic.");
                        viewUtils.handleRedirect(context, TOPICS_PATH + "/" + topicId + "/briefings");
                    },
                    () -> {
                        viewUtils.setErrorMessage(context, String.format("Topic with id %d not found.", topicId));
                        showAddForm(context);
                    }
            );
            return;
        }

        viewUtils.setSuccessMessage(context, "Content Source created successfully.");
        viewUtils.handleRedirect(context, UI_BASE_PATH);
    }
}
