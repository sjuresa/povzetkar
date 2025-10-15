package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import io.quarkus.panache.common.Page;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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

    private final ContentSourceDetailService contentSourceDetailService;

    @Inject
    public ContentSourceResource(ContentSourceDetailService contentSourceDetailService) {
        this.contentSourceDetailService = contentSourceDetailService;
    }

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
        if (contentSource.id != null && !id.equals(contentSource.id)) {
            throw new BadRequestException("The ID in the path does not match the ID in the request body.");
        }
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
        if (!ContentSource.deleteById(id)) {
            throw new NotFoundException("ContentSource with id " + id + " not found");
        }
        return Response.noContent().build();
    }

    @GET
    @Path("/users/{userId}/topics/{topicName}/content-sources")
    public List<ContentSource> getContentSourcesByTopic(
            @PathParam("userId") Long userId,
            @PathParam("topicName") @NotBlank String topicName,
            @QueryParam("page") @DefaultValue("0") @Min(0) int page,
            @QueryParam("size") @DefaultValue("10") @Min(1) @Max(100) int size) {

        Topic topic = Topic.<Topic>find("user.id = ?1 and name = ?2", userId, topicName).firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Topic '" + topicName + "' not found for user with id " + userId));

        Page panachePage = io.quarkus.panache.common.Page.of(page, size);
        return ContentSource.find("select cs from ContentSource cs join cs.topics t where t = ?1 order by cs.name", topic)
                .page(panachePage)
                .list();
    }

    @POST
    @Path("/content-sources/enrich")
    public Response enrichContentSources() {
        contentSourceDetailService.enrichContentSources();
        return Response.accepted().build();
    }

    @GET
    @Path("/content-sources/details/{channelId}")
    public ContentSourceDetailService.ContentSourceDetails getContentSourceDetails(@PathParam("channelId") String channelId) {
        return contentSourceDetailService.fetchContentSourceDetails(channelId)
                .orElseThrow(() -> new NotFoundException("Details for channel ID " + channelId + " not found"));
    }
}
