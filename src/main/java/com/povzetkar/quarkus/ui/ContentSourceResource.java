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
import jakarta.ws.rs.NotFoundException;

import java.util.Arrays;
import java.util.List;

@ApplicationScoped
public class ContentSourceResource {

    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm) {
        this.contentSourceForm = contentSourceForm;
    }

    @Route(path = "/ui/content-sources/add", methods = Route.HttpMethod.GET)
    @Blocking
    public void showAddForm(RoutingContext context) {
        Long topicId = Long.parseLong(context.request().getParam("topicId"));
        List<SourceTypeEnum> sourceTypes = Arrays.stream(SourceTypeEnum.values())
                .filter(st -> st != SourceTypeEnum.DEFAULT)
                .toList();
        context.response().end(contentSourceForm.data("topicId", topicId).data("sourceTypes", sourceTypes).render());
    }

    @Route(path = "/ui/content-sources", methods = Route.HttpMethod.POST)
    @Transactional
    @Blocking
    public void addContentSource(RoutingContext context) {
        String name = context.request().getFormAttribute("name");
        String sourceIdentifier = context.request().getFormAttribute("sourceIdentifier");
        String sourceTypeIdStr = context.request().getFormAttribute("sourceTypeId");
        String topicIdStr = context.request().getFormAttribute("topicId");

        if (name == null || name.isBlank()) {
            context.response().setStatusCode(400).end("Name cannot be blank.");
            return;
        }
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            context.response().setStatusCode(400).end("Source Identifier cannot be blank.");
            return;
        }
        Integer sourceTypeId = Integer.parseInt(sourceTypeIdStr);
        if (sourceTypeId == null || SourceTypeEnum.fromId(sourceTypeId) == SourceTypeEnum.DEFAULT) {
            context.response().setStatusCode(400).end("Invalid Source Type ID.");
            return;
        }

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.persist();

        if (topicIdStr != null && !topicIdStr.isBlank()) {
            Long topicId = Long.parseLong(topicIdStr);
            Topic topic = Topic.<Topic>findByIdOptional(topicId)
                    .orElseThrow(() -> new NotFoundException("Topic with id " + topicId + " not found."));
            topic.contentSources.add(contentSource);
            topic.persist();
            context.response().setStatusCode(303).putHeader("Location", "/ui/topics/" + topicId + "/briefings").end();
            return;
        }

        context.response().setStatusCode(303).putHeader("Location", "/ui").end();
    }
}
