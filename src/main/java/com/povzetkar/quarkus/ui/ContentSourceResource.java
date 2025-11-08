package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.povzetkar.quarkus.ui.TopicResource.TOPICS_PATH;
import static com.povzetkar.quarkus.ui.TopicResource.UI_BASE_PATH;
import static com.povzetkar.quarkus.util.WebUtils.getLongFormAttribute;
import static com.povzetkar.quarkus.util.WebUtils.getLongPathParam;
import static com.povzetkar.quarkus.util.WebUtils.getIntegerFormAttribute;

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


    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm) {
        this.contentSourceForm = contentSourceForm;
    }

    @Route(path = CONTENT_SOURCES_ADD_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        Long topicId = getLongPathParam(context, PARAM_TOPIC_ID, INVALID_OR_MISSING_TOPIC_ID_PARAM);
        if (topicId == null) {
            return;
        }
        List<SourceTypeEnum> sourceTypes = Arrays.stream(SourceTypeEnum.values())
                .filter(st -> st != SourceTypeEnum.DEFAULT)
                .toList();
        context.response().end(contentSourceForm.data(PARAM_TOPIC_ID, topicId).data("sourceTypes", sourceTypes).render());
    }

    @Route(path = CONTENT_SOURCES_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSource(RoutingContext context) {
        String name = context.request().getFormAttribute("name");
        String sourceIdentifier = context.request().getFormAttribute("sourceIdentifier");
        String topicIdStr = context.request().getFormAttribute(PARAM_TOPIC_ID);

        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Name cannot be blank.");
            return;
        }
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            context.response().setStatusCode(400).end("Source Identifier cannot be blank.");
            return;
        }
        Integer sourceTypeId = getIntegerFormAttribute(context, PARAM_SOURCE_TYPE_ID, INVALID_SOURCE_TYPE_ID_FORMAT);
        if (sourceTypeId == null) {
            return;
        }
        if (SourceTypeEnum.fromId(sourceTypeId) == SourceTypeEnum.DEFAULT) {
            context.response().setStatusCode(400).end(INVALID_SOURCE_TYPE_ID);
            return;
        }

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.persist();

        if (topicIdStr != null && !topicIdStr.isBlank()) {
            Long topicId = getLongFormAttribute(context, PARAM_TOPIC_ID, INVALID_TOPIC_ID_FORMAT);
            if (topicId == null) {
                return;
            }
            Optional<Topic> topicOptional = Topic.findByIdOptional(topicId);
            topicOptional.ifPresentOrElse(
                    topic -> {
                        topic.contentSources.add(contentSource);
                        topic.persist();
                        context.response().setStatusCode(303).putHeader("Location", TOPICS_PATH + "/" + topicId + "/briefings").end();
                    },
                    () -> context.response().setStatusCode(404).end(String.format("Topic with id %d not found.", topicId))
            );
            return;
        }

        context.response().setStatusCode(303).putHeader("Location", UI_BASE_PATH).end();
    }
}
