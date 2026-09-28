package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BackgroundJobDTO;
import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.dto.StatusUpdateDTO;
import com.povzetkar.quarkus.api.dto.SummaryDTO;
import com.povzetkar.quarkus.api.dto.TranscriptDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.Idempotent;
import com.povzetkar.quarkus.api.mapper.BackgroundJobMapper;
import com.povzetkar.quarkus.api.mapper.ContentItemMapper;
import com.povzetkar.quarkus.api.mapper.SummaryMapper;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import jakarta.transaction.Transactional;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.ContentItemAdminService;
import com.povzetkar.quarkus.service.UserContextService;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.api.util.QueryParser;
import io.quarkus.panache.common.Sort;
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
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Path("/api/v1/content-items")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Content Items", description = "Endpoints for managing Content Items")
public class ContentItemApiResource extends BaseApiResource {

    private static final String CONTENT_ITEM_WITH_ID = "ContentItem with id ";
    private static final String NOT_FOUND = " not found";

    private final ContentItemMapper contentItemMapper;
    private final BackgroundJobService backgroundJobService;
    private final BackgroundJobMapper backgroundJobMapper;
    private final SummaryMapper summaryMapper;
    private final ContentItemAdminService contentItemAdminService;

    @Inject
    public ContentItemApiResource(ContentItemMapper contentItemMapper, 
                                 BackgroundJobService backgroundJobService, 
                                 BackgroundJobMapper backgroundJobMapper, 
                                 SummaryMapper summaryMapper,
                                 ContentItemAdminService contentItemAdminService,
                                 UserContextService userContextService) {
        super(userContextService);
        this.contentItemMapper = contentItemMapper;
        this.backgroundJobService = backgroundJobService;
        this.backgroundJobMapper = backgroundJobMapper;
        this.summaryMapper = summaryMapper;
        this.contentItemAdminService = contentItemAdminService;
    }

    @GET
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Get content items", description = "Retrieves a paginated list of the content items visible to the caller (items of sources in the caller's topics or with a summary in the caller's briefings; all items for an admin), optionally filtered by sourceId, status, or topic. A sourceId or topicId outside the caller's scope is reported as not found.")
    @APIResponse(
            responseCode = "200",
            description = "List of content items retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<ContentItemDTO> getAllContentItems(
            @QueryParam("sourceId") Long sourceId,
            @QueryParam("status") List<ProcessingStatus> statuses,
            @QueryParam("topicId") Long topicId,
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        // Qualified with the item alias "c": the load query joins source and topics, which also have "id".
        // c.id last breaks ties, so rows with equal sort values neither repeat nor vanish across pages.
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("c.publishedAt").descending(), Set.of("id", "title", "publishedAt", "processingStatus"), "c")
                .and("c.id", Sort.Direction.Descending);

        ContentScope scope = getContentScope();
        if (sourceId != null && ContentSource.findVisibleById(sourceId, scope).isEmpty()) {
            throw new NotFoundException("Source with id " + sourceId + " not found.");
        }
        if (topicId != null && findTopicInScope(topicId, scope).isEmpty()) {
            throw new NotFoundException("Topic with id " + topicId + " not found.");
        }

        PanacheQuery<Long> idQuery = ContentItem.findVisibleIds(scope, new ContentItem.Filter(sourceId, statuses, topicId), panacheSort);

        long count = idQuery.count();
        List<Long> itemIds = idQuery.page(pageIndex, clampedPageSize).list();

        if (itemIds.isEmpty()) {
            return new PaginatedResponse<>(List.of(), count, pageIndex, clampedPageSize);
        }

        List<ContentItem> items = ContentItem.listByIdsWithAssociations(itemIds, panacheSort);

        // Fetch all summary existence flags in one go to solve N+1 Problem
        final Set<Long> itemIdsWithSummaries = Summary.findContentItemIdsWithSummary(itemIds);

        List<ContentItemDTO> dtoList = items.stream().map(item -> contentItemMapper.toDTO(item, itemIdsWithSummaries, scope)).toList();
        return new PaginatedResponse<>(dtoList, count, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Get content item by ID", description = "Retrieves a specific content item by its ID. The item must be visible to the caller (its source is in one of the caller's topics, or its summary is in one of the caller's briefings; an admin sees all); otherwise not found.")
    @APIResponse(
            responseCode = "200",
            description = "Content item details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentItemDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the content item to retrieve", schema = @Schema(type = SchemaType.INTEGER))
    public ContentItemDTO getContentItemById(@PathParam("id") Long id) {
        return mapToDTO(findVisibleItem(id));
    }

    @GET
    @Path("/{id}/summary")
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Get content item summary", description = "Retrieves the AI-generated summary for a specific content item. The item must be visible to the caller (its source is in one of the caller's topics, or its summary is in one of the caller's briefings; an admin sees all); otherwise not found.")
    @APIResponse(
            responseCode = "200",
            description = "Summary retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = SummaryDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item or summary not found")
    public SummaryDTO getSummaryByItemId(@PathParam("id") Long id) {
        return summaryMapper.toDTO(findVisibleSummary(id));
    }

    @GET
    @Path("/{id}/transcript")
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Get content item transcript", description = "Retrieves the raw transcript for a specific content item. The item must be visible to the caller (its source is in one of the caller's topics, or its summary is in one of the caller's briefings; an admin sees all); otherwise not found.")
    @APIResponse(
            responseCode = "200",
            description = "Transcript retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = TranscriptDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item or transcript not found")
    public TranscriptDTO getTranscriptByItemId(@PathParam("id") Long id) {
        ContentItem item = findVisibleItem(id);
        if (item instanceof YouTubeVideo video && video.transcript != null && !video.transcript.isBlank()) {
            return new TranscriptDTO(id, video.transcript);
        }
        throw new NotFoundException("Transcript for ContentItem " + id + NOT_FOUND);
    }

    @POST
    @Path("/{id}/commands/generate-summary")
    @RolesAllowed(Permission.V_SUMMARY_GENERATE)
    @Idempotent
    @Operation(summary = "Generate summary for item", description = "Force an individual content item to fetch transcript and generate a summary. The item must be visible to the caller; otherwise not found.")
    @APIResponse(
            responseCode = "202",
            description = "Summary generation triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @APIResponse(responseCode = "409", description = "Conflict or already processing")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the content item to generate summary for", schema = @Schema(type = SchemaType.INTEGER))
    public Response generateItemSummary(@PathParam("id") Long id, @Context UriInfo uriInfo) {
        User user = getAuthenticatedUser();
        ContentItem item = requireActionable(findVisibleItem(id));

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
    @RolesAllowed(Permission.V_ADMIN_JOB_UPDATE)
    @Idempotent
    @Operation(summary = "Retry failed item", description = "Signals to the background worker to retry a failed item. Admin functionality.")
    @APIResponse(
            responseCode = "202",
            description = "Retry triggered",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = BackgroundJobDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @APIResponse(responseCode = "409", description = "Conflict or invalid state")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the content item to retry", schema = @Schema(type = SchemaType.INTEGER))
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

    @POST
    @Path("/{id}/commands/status")
    @RolesAllowed(Permission.V_ADMIN_JOB_UPDATE)
    @Operation(summary = "Override content item status", description = "Manually updates the processing status of a content item. Admin only.")
    @APIResponse(
            responseCode = "200",
            description = "Status updated successfully",
            content = @Content(mediaType = MediaType.APPLICATION_JSON, schema = @Schema(implementation = ContentItemDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Content item not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the content item to update", schema = @Schema(type = SchemaType.INTEGER))
    public ContentItemDTO updateItemStatus(@PathParam("id") Long id, StatusUpdateDTO dto) {
        ContentItem itemToUpdate = ContentItem.findByIdWithAssociations(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND));
        
        ContentItem updatedItem = contentItemAdminService.updateStatus(itemToUpdate, dto.status(), "ADMIN");
        return mapToDTO(updatedItem);
    }

    @POST
    @Path("/{id}/summary/share")
    @Transactional
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Enable sharing for a summary", description = "Generates a public UUID for the summary of a specific content item. The item must be visible to the caller; otherwise not found.")
    @APIResponse(responseCode = "200", description = "Sharing enabled successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class)))
    @APIResponse(responseCode = "404", description = "Summary not found")
    public String shareSummary(@PathParam("id") Long id) {
        Summary summary = findVisibleSummary(id);
        summary.share();
        return summary.uuid;
    }

    @POST
    @Path("/{id}/summary/unshare")
    @Transactional
    @RolesAllowed(Permission.V_CONTENT_ITEM_VIEW)
    @Operation(summary = "Disable sharing for a summary", description = "Removes the public UUID for the summary to disable unauthenticated access. The item must be visible to the caller; otherwise not found.")
    @APIResponse(responseCode = "204", description = "Sharing disabled successfully")
    @APIResponse(responseCode = "404", description = "Summary not found")
    public void unshareSummary(@PathParam("id") Long id) {
        Summary summary = findVisibleSummary(id);
        summary.unshare();
    }

    /** The item if visible to the caller; an invisible item is reported as not found (POV-219). */
    private ContentItem findVisibleItem(Long id) {
        return ContentItem.findVisibleByIdWithAssociations(id, getContentScope())
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND));
    }

    private Summary findVisibleSummary(Long contentItemId) {
        return Summary.findVisibleByContentIdWithAssociations(contentItemId, getContentScope())
                .orElseThrow(() -> new NotFoundException("Summary for ContentItem " + contentItemId + NOT_FOUND));
    }

    private Optional<Topic> findTopicInScope(Long topicId, ContentScope scope) {
        return scope.isUnrestricted() ? Topic.findByIdOptional(topicId) : userContextService.findOwnedTopic(topicId);
    }

    private ContentItem findAndValidateContentItemForAction(Long id) {
        return requireActionable(ContentItem.<ContentItem>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_ITEM_WITH_ID + id + NOT_FOUND)));
    }

    private static ContentItem requireActionable(ContentItem item) {
        if (item.processingStatus == ProcessingStatus.COMPLETED) {
            throw new WebApplicationException(
                    "Cannot perform this action on an item in status " + item.processingStatus,
                    Response.Status.CONFLICT);
        }
        return item;
    }

    private ContentItemDTO mapToDTO(ContentItem item) {
        Set<Long> summaryExists = Summary.count("contentItem.id", item.id) > 0 
                ? Set.of(item.id) : Set.of();
        return contentItemMapper.toDTO(item, summaryExists, getContentScope());
    }
}
