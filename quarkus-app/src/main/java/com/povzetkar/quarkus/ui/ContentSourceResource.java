package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.qute.TemplateInstance;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.FormParam;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.net.URI;
import java.util.Arrays;
import java.util.List;

@ApplicationScoped
@Path("/ui/content-sources")
public class ContentSourceResource {

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
            Topic topic = Topic.findById(topicId);
            if (topic != null) {
                topic.contentSources.add(contentSource);
                topic.persist();
                return Response.seeOther(URI.create("/ui/topics/" + topicId + "/briefings")).build();
            }
        }

        return Response.seeOther(URI.create("/ui")).build();
    }
}
