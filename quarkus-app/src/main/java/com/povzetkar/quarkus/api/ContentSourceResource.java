package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.List;
import java.util.Optional;

@Path("/api/v1")
@ApplicationScoped
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
        return ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("ContentSource with id " + id + " not found"));
    }

    @POST
    @Path("/content-sources")
    @Transactional
    public Response createContentSource(@Valid @NotNull ContentSource contentSource) {
        Optional<ContentSource> existing = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2",
                contentSource.sourceIdentifier, contentSource.sourceTypeId).firstResultOptional();

        if (existing.isPresent()) {
            throw new WebApplicationException("ContentSource with the same sourceIdentifier and sourceTypeId already exists.", Response.Status.CONFLICT);
        }

        contentSource.persist();
        return Response.status(Response.Status.CREATED).entity(contentSource).build();
    }

    @PUT
    @Path("/content-sources/{id}")
    @Transactional
    public ContentSource updateContentSource(@PathParam("id") Long id, @Valid @NotNull ContentSource contentSource) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException("ContentSource with id " + id + " not found"));

        Optional<ContentSource> existingWithSameKey = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2 and id != ?3",
                contentSource.sourceIdentifier, contentSource.sourceTypeId, id).firstResultOptional();

        if (existingWithSameKey.isPresent()) {
            throw new WebApplicationException("Another ContentSource with the same sourceIdentifier and sourceTypeId already exists.", Response.Status.CONFLICT);
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
    public Response deleteContentSource(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.findById(id);
        if (entity == null) {
            throw new NotFoundException("ContentSource with id " + id + " not found");
        }
        entity.delete();
        return Response.noContent().build();
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
