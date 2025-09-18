package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import jakarta.transaction.Transactional;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;

@Path("/api/v1")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ContentSourceResource {

    @GET
    @Path("/content-sources")
    public List<ContentSource> getAllContentSources() {
        return ContentSource.listAll();
    }

    @GET
    @Path("/content-sources/{id}")
    public ContentSource getContentSourceById(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.findById(id);
        if (entity == null) {
            throw new NotFoundException("ContentSource with id " + id + " not found");
        }
        return entity;
    }

    @POST
    @Path("/content-sources")
    @Transactional
    public Response createContentSource(ContentSource contentSource) {
        if (contentSource == null || contentSource.name == null || contentSource.name.trim().isEmpty()) {
            throw new BadRequestException("ContentSource name cannot be blank");
        }
        contentSource.persist();
        return Response.status(Response.Status.CREATED).entity(contentSource).build();
    }

    @PUT
    @Path("/content-sources/{id}")
    @Transactional
    public ContentSource updateContentSource(@PathParam("id") Long id, ContentSource contentSource) {
        ContentSource entity = ContentSource.findById(id);
        if (entity == null) {
            throw new NotFoundException();
        }
        if (contentSource == null || contentSource.name == null || contentSource.name.trim().isEmpty()) {
            throw new BadRequestException("ContentSource name cannot be blank");
        }
        entity.sourceIdentifier = contentSource.sourceIdentifier;
        entity.sourceTypeId = contentSource.sourceTypeId;
        entity.name = contentSource.name;
        entity.description = contentSource.description;
        entity.subscriberCount = contentSource.subscriberCount;
        return entity;
    }

    @DELETE
    @Path("/content-sources/{id}")
    @Transactional
    public void deleteContentSource(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.findById(id);
        if (entity == null) {
            throw new NotFoundException();
        }
        entity.delete();
    }

    @GET
    @Path("/users/{userId}/topics/{topicName}/content-sources")
    public List<ContentSource> getContentSourcesByTopic(
            @PathParam("userId") Long userId,
            @PathParam("topicName") @NotBlank String topicName,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size) {

        User user = User.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        Topic topic = Topic.find("user = ?1 and name = ?2", user, topicName).firstResult();
        if (topic == null) {
            throw new NotFoundException("Topic not found for user");
        }

        io.quarkus.panache.common.Page panachePage = io.quarkus.panache.common.Page.of(page, size);
        return ContentSource.find("select cs from ContentSource cs join cs.topics t where t = ?1 order by cs.name", topic)
                .page(panachePage)
                .list();
    }
}
