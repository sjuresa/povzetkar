package com.povzetkar.quarkus.api;

import jakarta.ws.rs.*;
import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
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
import io.quarkus.panache.common.Sort;

import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Set;

@Path("/api/v1/content-items")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Items", description = "Endpoints for managing Content Items")
public class ContentItemApiResource {

    private static final String CONTENT_ITEM_WITH_ID = "ContentItem with id ";
    private static final String NOT_FOUND = " not found";

    private final ContentItemMapper contentItemMapper;
    private final UserContextService userContextService;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;

    @Inject
    public ContentItemApiResource(ContentItemMapper contentItemMapper, UserContextService userContextService, BackgroundJobService backgroundJobService, BackgroundJobMapper backgroundJobMapper) {
        this.contentItemMapper = contentItemMapper;
        this.userContextService = userContextService;
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
    }

    @GET
    @RolesAllowed("CONTENT_ITEM_VIEW")
    @Operation(summary = "Get all content items", description = "Retrieves a paginated list of all content items, optionally filtered by sourceId.")
    public PaginatedResponse<ContentItemDTO> getAllContentItems(
            @QueryParam("sourceId") Long sourceId,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        PanacheQuery<ContentItem> query;
        if (sourceId != null) {
            ContentSource source = ContentSource.findById(sourceId);
            if (source == null) {
                throw new NotFoundException("Source with id " + sourceId + " not found.");
            }
            query = ContentItem.find("contentSource", Sort.by("publishedAt").descending(), source);
        } else {
            query = ContentItem.findAll(Sort.by("publishedAt").descending());
        }

        query.page(pageIndex, pageSize);

        List<ContentItemDTO> dtoList = query.stream().map(contentItemMapper::toDTO).toList();
        long count = query.count();

        return new PaginatedResponse<>(dtoList, count);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("CONTENT_ITEM_VIEW")
    @Operation(summary = "Get content item by ID", description = "Retrieves a specific content item by its ID.")
    public ContentItemDTO getContentItemById(@PathParam("id") Long id) {
        ContentItem entity = ContentItem.<ContentItem>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND));
        return contentItemMapper.toDTO(entity);
    }

    @POST
    @Path("/{id}/commands/generate-summary")
    @RolesAllowed("SUMMARY_GENERATE")
    @Operation(summary = "Generate summary for item", description = "Force an individual content item to fetch transcript and generate a summary.")
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
                .entity(backgroundJobMapper.toDTO(job))
                .build();
    }

    @POST
    @Path("/{id}/commands/retry")
    @RolesAllowed("ADMIN_JOB_UPDATE")
    @Operation(summary = "Retry failed item", description = "Signals to the background worker to retry a failed item. Admin functionality.")
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
                .entity(backgroundJobMapper.toDTO(job))
                .build();
    }

    private User getAuthenticatedUser() {
        return userContextService.getCurrentUser()
                .orElseThrow(() -> new NotAuthorizedException("User must be authenticated"));
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
}
