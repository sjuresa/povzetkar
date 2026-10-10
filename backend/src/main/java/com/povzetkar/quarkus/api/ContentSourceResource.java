package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.dto.ContentSourceDTO;
import com.povzetkar.quarkus.api.dto.ContentSourcePaginatedResponse;
import com.povzetkar.quarkus.api.dto.ContentSourcePatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.OptimisticLocking;
import com.povzetkar.quarkus.api.mapper.ContentSourceMapper;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.ContentSourceService;
import com.povzetkar.quarkus.service.SourceDetailsUnavailableException;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/sources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Content Sources", description = "Endpoints for managing Content Sources")
public class ContentSourceResource extends BaseApiResource {

    public static final String CONTENT_SOURCE_WITH_ID = "ContentSource with id ";
    public static final String NOT_FOUND = " not found";
    private final ContentSourceDetailService contentSourceDetailService;
    private final ContentSourceService contentSourceService;
    private final ContentSourceMapper contentSourceMapper;

    @Inject
    public ContentSourceResource(ContentSourceDetailService contentSourceDetailService,
                                 ContentSourceService contentSourceService,
                                 ContentSourceMapper contentSourceMapper,
                                 UserContextService userContextService) {
        super(userContextService);
        this.contentSourceDetailService = contentSourceDetailService;
        this.contentSourceService = contentSourceService;
        this.contentSourceMapper = contentSourceMapper;
    }

    @GET
    @RolesAllowed(Permission.V_SOURCE_VIEW)
    @Operation(summary = "Get content sources",
            description = "Retrieves a paginated list of the content sources visible to the caller: the sources in the caller's topics (all sources for an admin).")
    @APIResponse(
            responseCode = "200",
            description = "List of content sources retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourcePaginatedResponse.class))
    )
    public ContentSourcePaginatedResponse getAllContentSources(
            @QueryParam("search") @DefaultValue("") String search,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize,
            @QueryParam("excludeIds") String excludeIdsCsv) {

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        
        List<Long> excludeIds = null;
        if (excludeIdsCsv != null && !excludeIdsCsv.isBlank()) {
            try {
                excludeIds = Arrays.stream(excludeIdsCsv.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .map(Long::valueOf)
                        .toList();
            } catch (NumberFormatException e) {
                throw new BadRequestException("Invalid format for excludeIds. All IDs must be numeric.", e);
            }
        }

        PanacheQuery<ContentSource> query = ContentSource.findVisible(getContentScope(), search, excludeIds);

        long count = query.count();
        List<ContentSourceDTO> dtoList = query.page(pageIndex, clampedPageSize)
                .stream()
                .map(contentSourceMapper::toDTO)
                .toList();
 
        return new ContentSourcePaginatedResponse(dtoList, count, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(Permission.V_SOURCE_VIEW)
    @Operation(summary = "Get content source by ID",
            description = "Retrieves a content source visible to the caller; a source outside the caller's topics is reported as not found.")
    @APIResponse(
            responseCode = "200",
            description = "Content source retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Source not found")
    public ContentSourceDTO getContentSourceById(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.findVisibleById(id, getContentScope())
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));
        return contentSourceMapper.toDTO(entity);
    }

    @POST
    @Transactional
    @RolesAllowed(Permission.V_SOURCE_CREATE)
    @Operation(summary = "Create content source",
            description = "Admin only. Resolves the identifier (handle, URL or channel ID) to the channel ID and returns the existing source for it (200) or creates one (201). Users add sources through a topic's newSources.")
    @APIResponse(
            responseCode = "201",
            description = "Content source created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
    )
    @APIResponse(responseCode = "400", description = "Invalid or unsupported platform, or the channel does not exist")
    public Response createContentSource(@Valid @NotNull ContentSourceCreateDTO createDTO) {
        ContentSourceService.Result result = contentSourceService.findOrCreate(createDTO);
        ContentSource contentSource = result.source();
        URI location = URI.create("/api/v1/sources/" + contentSource.id);
        Response.ResponseBuilder response = result.created() ? Response.created(location) : Response.ok().location(location);
        return response
                .entity(contentSourceMapper.toDTO(contentSource))
                .type(MediaType.APPLICATION_JSON)
                .build();
    }


    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed(Permission.V_SOURCE_UPDATE)
    @Operation(summary = "Partially update content source", description = "Partially updates an existing content source.")
    @APIResponse(
           responseCode = "200",
           description = "Content source patched successfully",
           content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
   )
   @OptimisticLocking
   public ContentSourceDTO patchContentSource(@PathParam("id") Long id,
                                              @HeaderParam("If-Match") String ifMatch,
                                              @Valid @NotNull ContentSourcePatchDTO patchDTO) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));

        checkOptimisticLock(entity, ifMatch);
        contentSourceMapper.patchEntity(patchDTO, entity);
        
        checkForDuplicateContentSource(entity.sourceIdentifier, entity.sourceTypeId, id);
        
        return contentSourceMapper.toDTO(entity);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed(Permission.V_SOURCE_DELETE)
    @OptimisticLocking
    @Operation(summary = "Delete content source", description = "Deletes a specific content source.")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the source to delete")
    @Parameter(name = "If-Match", in = ParameterIn.HEADER, required = false, description = "Optimistic locking version")
    public Response deleteContentSource(@PathParam("id") Long id, @HeaderParam("If-Match") String ifMatch) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));
                
        checkOptimisticLock(entity, ifMatch);
        
        entity.delete();
        return Response.noContent().build();
    }

    // Maintain existing enrich functionality for compatibility
    @POST
    @Path("/enrich")
    @RolesAllowed(Permission.V_ADMIN_SYSTEM_TRIGGER)
    public Response enrichContentSources() {
        contentSourceDetailService.enrichContentSources();
        return Response.accepted().entity("Content sources enrichment started.").build();
    }

    @GET
    @Path("/details/{sourceTypeId}/{identifier}")
    @RolesAllowed(Permission.V_SOURCE_VIEW)
    public ContentSourceDetailService.ContentSourceDetails getContentSourceDetails(
            @PathParam("sourceTypeId") int sourceTypeId, @PathParam("identifier") String identifier) {
        try {
            return contentSourceDetailService.fetchContentSourceDetails(sourceTypeId, identifier)
                    .orElseThrow(() -> new NotFoundException("Details for source type "
                            + SourceTypeEnum.fromId(sourceTypeId) + " with identifier " + identifier + NOT_FOUND));
        } catch (SourceDetailsUnavailableException e) {
            // not 404: the frontend shows "check unavailable" instead of "channel not found"
            throw new BusinessRuleException(Response.Status.SERVICE_UNAVAILABLE, "SOURCE_CHECK_UNAVAILABLE",
                    "The channel check is unavailable right now; please try again later");
        }
    }

    private void checkForDuplicateContentSource(String sourceIdentifier, int sourceTypeId, Long excludeId) {
        Optional<ContentSource> existing = ContentSource.findByIdentifier(sourceIdentifier, sourceTypeId);

        if (existing.isPresent() && (excludeId == null || !existing.get().id.equals(excludeId))) {
            throw new WebApplicationException(
                    "ContentSource with the same sourceIdentifier and platform already exists.",
                    Response.Status.CONFLICT);
        }
    }

}
