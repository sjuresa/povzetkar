package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Path("")
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
        return ContentSource.findById(id);
    }

    @POST
    @Path("/content-sources")
    @Transactional
    public Response createContentSource(ContentSource contentSource) {
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
    @Path("/api/v1/users/{userId}/topics/{topicName}/content-sources")
    public List<ContentSource> getContentSourcesByTopic(
            @PathParam("userId") Long userId,
            @PathParam("topicName") String topicName,
            @QueryParam("page") @DefaultValue("0") int page,
            @QueryParam("size") @DefaultValue("10") int size) {

        if (topicName == null || topicName.trim().isEmpty()) {
            throw new BadRequestException("Topic name cannot be blank");
        }

        if (page < 0) {
            throw new BadRequestException("Page number cannot be negative");
        }
        if (size <= 0 || size > 100) {
            throw new BadRequestException("Page size must be between 1 and 100");
        }

        User user = User.findById(userId);
        if (user == null) {
            throw new NotFoundException("User not found");
        }

        Topic topic = Topic.find("user = ?1 and name = ?2", user, topicName).firstResult();
        if (topic == null) {
            throw new NotFoundException("Topic not found for user");
        }

        List<ContentSource> allContentSources = new ArrayList<>(topic.contentSources);

        int fromIndex = page * size;
        if (fromIndex >= allContentSources.size()) {
            return Collections.emptyList();
        }
        int toIndex = Math.min(fromIndex + size, allContentSources.size());

        return allContentSources.subList(fromIndex, toIndex);
    }
}
