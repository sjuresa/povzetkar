package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.service.UserContextService;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.FormValidator;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.povzetkar.quarkus.ui.TopicResource.TOPICS_PATH;
import static com.povzetkar.quarkus.ui.TopicResource.UI_BASE_PATH;

@ApplicationScoped
public class ContentSourceResource {

    private static final Logger logger = LoggerFactory.getLogger(ContentSourceResource.class);

    public static final String CONTENT_SOURCES_PATH = UI_BASE_PATH + "/content-sources";
    public static final String CONTENT_SOURCES_ADD_PATH = CONTENT_SOURCES_PATH + "/add";
    private static final String PARAM_TOPIC_ID = "topicId";
    private static final String INVALID_OR_MISSING_TOPIC_ID_PARAM = "Invalid or missing topicId parameter.";
    private static final String PARAM_SOURCE_TYPE_ID = "sourceTypeId";
    private static final String INVALID_SOURCE_TYPE_ID_FORMAT = "Invalid format for Source Type ID.";
    private static final String INVALID_SOURCE_TYPE_ID = "A valid source type must be selected.";


    private final ViewUtils viewUtils;
    private final UserContextService userContextService;
    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm,
                                 ViewUtils viewUtils,
                                 UserContextService userContextService) {
        this.contentSourceForm = contentSourceForm;
        this.viewUtils = viewUtils;
        this.userContextService = userContextService;
    }

    public static String topicBriefingsPath(long topicId) {
        return TopicResource.TOPIC_BRIEFINGS_PATH.replace(TopicResource.TOPIC_ID_PATH_PARAM, String.valueOf(topicId));
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
        // Verify topic ownership before rendering the form
        if (userContextService.findOwnedTopic(topicIdOptional.get()).isEmpty()) {
            context.response().setStatusCode(404).end(String.format(TopicResource.TOPIC_NOT_FOUND_MSG, topicIdOptional.get()));
            return;
        }
        renderAddFormWithData(context, topicIdOptional.get(), null, null, null); // Fresh form
    }

    @Route(path = CONTENT_SOURCES_PATH, methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSource(RoutingContext context) {
        FormValidator validator = new FormValidator();
        String name = context.request().getFormAttribute("name");
        String sourceIdentifier = context.request().getFormAttribute("sourceIdentifier");

        logger.info("Validating content source. Name: '{}', SourceIdentifier: '{}'", name, sourceIdentifier);

        validator.validateNotBlank(name, "Name cannot be blank.");
        validator.validateNotBlank(sourceIdentifier, "Source Identifier cannot be blank.");

        Optional<Long> topicIdOptional = WebUtils.getLongFormAttribute(context, PARAM_TOPIC_ID, "Invalid format for topic ID.", validator::addError);
        validator.validateNotNull(topicIdOptional.orElse(null), "Topic ID is required.");

        Optional<Integer> sourceTypeIdOptional = WebUtils.getIntegerFormAttribute(context, PARAM_SOURCE_TYPE_ID, INVALID_SOURCE_TYPE_ID_FORMAT, validator::addError);
        validator.validateNotNull(sourceTypeIdOptional.orElse(null), INVALID_SOURCE_TYPE_ID);

        Integer sourceTypeIdForRender = sourceTypeIdOptional.orElse(null); // Value for re-rendering, null if validation failed
        if (sourceTypeIdForRender != null && SourceTypeEnum.fromId(sourceTypeIdForRender) == SourceTypeEnum.DEFAULT) {
            validator.addError(INVALID_SOURCE_TYPE_ID);
        }

        if (validator.hasErrors()) {
            viewUtils.setErrorMessage(context, String.join("; ", validator.getErrors()));
            renderAddFormWithData(context, topicIdOptional.orElse(null), name, sourceIdentifier, sourceTypeIdForRender);
            return;
        }

        // All validations passed, it's safe to get the values
        Long topicId = topicIdOptional.get();
        Integer sourceTypeId = sourceTypeIdOptional.get();

        // Verify topic ownership before proceeding
        Optional<Topic> topicOptional = userContextService.findOwnedTopic(topicId);
        if (topicOptional.isEmpty()) {
            context.response().setStatusCode(404).end(String.format(TopicResource.TOPIC_NOT_FOUND_MSG, topicId));
            return;
        }
        Topic topic = topicOptional.get();

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;

        // Check if content source already exists globally
        Optional<ContentSource> existingContentSource = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2", sourceIdentifier, sourceTypeId).firstResultOptional();
        if (existingContentSource.isPresent()) {
            contentSource = existingContentSource.get(); // Use existing source
        } else {
            contentSource.persist(); // Persist new source
        }

        // Add content source to topic
        topic.contentSources.add(contentSource);
        topic.persist();
        viewUtils.setSuccessMessage(context, "Content Source created/added and linked to topic.");
        viewUtils.handleRedirect(context, TopicResource.topicBriefingsPath(topicId));
    }
}
