package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.mapper.ContentItemMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Path("/api/v1/content-items")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Items", description = "Endpoints for managing Content Items")
public class ContentItemApiResource extends BaseApiResource {

    private static final String CONTENT_ITEM_WITH_ID = "ContentItem with id ";
    private static final String NOT_FOUND = " not found";

    private final ContentItemMapper contentItemMapper;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;

    @Inject
    public ContentItemApiResource(ContentItemMapper contentItemMapper, BackgroundJobService backgroundJobService, BackgroundJobMapper backgroundJobMapper, UserContextService userContextService) {
        super(userContextService);
        this.contentItemMapper = contentItemMapper;
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
    }

    @GET
    @RolesAllowed("CONTENT_ITEM_VIEW")
    @Operation(summary = "Get all content items", description = "Retrieves a paginated list of all content items, optionally filtered by sourceId.")
    @APIResponse(
            responseCode = "200",
            description = "List of content items retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentItemListEnvelope.class))
    )
    public ApiEnvelopeDTO<List<ContentItemDTO>> getAllContentItems(
            @QueryParam("sourceId") Long sourceId,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        PanacheQuery<Long> idQuery;
        if (sourceId != null) {
            ContentSource source = ContentSource.findById(sourceId);
            if (source == null) {
                throw new NotFoundException("Source with id " + sourceId + " not found.");
            }
            idQuery = ContentItem.find("SELECT c.id FROM ContentItem c WHERE c.contentSource = ?1 ORDER BY c.publishedAt DESC", source).project(Long.class);
        } else {
            idQuery = ContentItem.find("SELECT c.id FROM ContentItem c ORDER BY c.publishedAt DESC").project(Long.class);
        }

        long count = idQuery.count();
        List<Long> itemIds = idQuery.page(pageIndex, pageSize).list();

        if (itemIds.isEmpty()) {
            return ApiEnvelopeDTO.paginated(List.of(), count, pageIndex, pageSize);
        }

        List<ContentItem> items = ContentItem.list("FROM ContentItem c LEFT JOIN FETCH c.contentSource WHERE c.id IN ?1 ORDER BY c.publishedAt DESC", itemIds);
        List<ContentItemDTO> dtoList = items.stream().map(contentItemMapper::toDTO).toList();
        return ApiEnvelopeDTO.paginated(dtoList, count, pageIndex, pageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("CONTENT_ITEM_VIEW")
    @Operation(summary = "Get content item by ID", description = "Retrieves a specific content item by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Content item details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentItemEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    public ApiEnvelopeDTO<ContentItemDTO> getContentItemById(@PathParam("id") Long id) {
        ContentItem entity = ContentItem.<ContentItem>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND));
        return ApiEnvelopeDTO.success(contentItemMapper.toDTO(entity));
    }

    @POST
    @Path("/{id}/commands/generate-summary")
    @RolesAllowed("SUMMARY_GENERATE")
    @Operation(summary = "Generate summary for item", description = "Force an individual content item to fetch transcript and generate a summary.")
    @APIResponse(
            responseCode = "202",
            description = "Summary generation triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = JobEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @APIResponse(responseCode = "409", description = "Conflict or already processing")
    public Response generateItemSummary(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        User user = getAuthenticatedUser();
        ContentItem item = findAndValidateContentItemForAction(id);

        BackgroundJob job = backgroundJobService.triggerItemJob(item, JobType.GENERATE_ITEM_SUMMARY, user.id);

        URI location = uriInfo.getBaseUriBuilder()
                .path(JobApiResource.class)
                .path(JobApiResource.class, "getJobById")
                .build(job.id);

        return Response.accepted()
                .location(location)
                .entity(ApiEnvelopeDTO.success(backgroundJobMapper.toDTO(job)))
                .build();
    }

    @POST
    @Path("/{id}/commands/retry")
    @RolesAllowed("ADMIN_JOB_UPDATE")
    @Operation(summary = "Retry failed item", description = "Signals to the background worker to retry a failed item. Admin functionality.")
    @APIResponse(
            responseCode = "202",
            description = "Retry triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = JobEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @APIResponse(responseCode = "409", description = "Conflict or invalid state")
    public Response retryItemProcessing(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        User user = getAuthenticatedUser();
        ContentItem item = findAndValidateContentItemForAction(id);

        BackgroundJob job = backgroundJobService.triggerItemJob(item, JobType.RETRY_ITEM_PROCESSING, user.id);

        // Uses AdminApiResource since this is an admin functionality and links to global jobs view (if desired)
        URI location = uriInfo.getBaseUriBuilder()
                .path(AdminApiResource.class)
                .path(AdminApiResource.class, "getJobById")
                .build(job.id);

        return Response.accepted()
                .location(location)
                .entity(ApiEnvelopeDTO.success(backgroundJobMapper.toDTO(job)))
                .build();
    }

 

    private ContentItem findAndValidateContentItemForAction(Long id) {
        ContentItem item = ContentItem.<ContentItem>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND));

        if (Set.of(ProcessingStatus.PENDING, ProcessingStatus.COMPLETED).contains(item.processingStatus)) {
            throw new WebApplicationException(
                    "Cannot perform this action on an item in status " + item.processingStatus,
                    Response.Status.CONFLICT);
        }
        return item;
    }

    // Helper classes for OpenAPI documentation
    private static class JobEnvelope extends ApiEnvelopeDTO<BackgroundJobDTO> {}
    private static class ContentItemEnvelope extends ApiEnvelopeDTO<ContentItemDTO> {}
    private static class ContentItemListEnvelope extends ApiEnvelopeDTO<List<ContentItemDTO>> {}
}
