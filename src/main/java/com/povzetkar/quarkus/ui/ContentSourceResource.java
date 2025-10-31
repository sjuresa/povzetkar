package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;

import java.util.Arrays;
import java.util.List;

import static com.povzetkar.quarkus.ui.TopicResource.LIST_BRIEFINGS_BY_TOPIC;

@ApplicationScoped
@Path("/ui/content-sources")
public class ContentSourceResource {

    @Context
    UriInfo uriInfo;

    private final Template contentSourceForm;

    public ContentSourceResource(@Location("content-source-form.html") Template contentSourceForm) {
        this.contentSourceForm = contentSourceForm;
    }

    @GET
    @Path("/add")
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance showAddForm(@QueryParam("topicId") Long topicId) {
        List<SourceTypeEnum> sourceTypes = Arrays.stream(SourceTypeEnum.values())
                .filter(st -> st != SourceTypeEnum.DEFAULT)
                .toList();
        return contentSourceForm.data("topicId", topicId).data("sourceTypes", sourceTypes);
    }

    @POST
    @Transactional
    @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
    public Response addContentSource(@FormParam("name") String name,
                                     @FormParam("sourceIdentifier") String sourceIdentifier,
                                     @FormParam("sourceTypeId") Integer sourceTypeId,
                                     @FormParam("topicId") Long topicId) {

        if (name == null || name.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Name cannot be blank.").build();
        }
        if (sourceIdentifier == null || sourceIdentifier.isBlank()) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Source Identifier cannot be blank.").build();
        }
        if (sourceTypeId == null || SourceTypeEnum.fromId(sourceTypeId) == SourceTypeEnum.DEFAULT) {
            return Response.status(Response.Status.BAD_REQUEST).entity("Invalid Source Type ID.").build();
        }

        ContentSource contentSource = new ContentSource();
        contentSource.name = name;
        contentSource.sourceIdentifier = sourceIdentifier;
        contentSource.sourceTypeId = sourceTypeId;
        contentSource.persist();

        if (topicId != null) {
            Topic topic = Topic.<Topic>findByIdOptional(topicId)
                    .orElseThrow(() -> new NotFoundException("Topic with id " + topicId + " not found."));
            topic.contentSources.add(contentSource);
            topic.persist();
            return Response.seeOther(uriInfo.getBaseUriBuilder().path(TopicResource.class).path(TopicResource.class, LIST_BRIEFINGS_BY_TOPIC).build(topicId)).build();
        }

        return Response.seeOther(uriInfo.getBaseUriBuilder().path("/ui").build()).build();
    }
}
