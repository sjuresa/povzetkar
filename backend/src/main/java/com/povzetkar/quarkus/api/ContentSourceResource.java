package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.dto.ContentSourceDTO;
import com.povzetkar.quarkus.api.dto.ContentSourcePaginatedResponse;
import com.povzetkar.quarkus.api.dto.ContentSourcePatchDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.filter.OptimisticLocking;
import com.povzetkar.quarkus.api.mapper.ContentSourceMapper;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tags;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
    private static final Logger logger = LoggerFactory.getLogger(ContentSourceResource.class);
    
    private final ContentSourceDetailService contentSourceDetailService;
    private final ContentSourceMapper contentSourceMapper;
    private final MeterRegistry registry;

    @Inject
    public ContentSourceResource(ContentSourceDetailService contentSourceDetailService,
                                 ContentSourceMapper contentSourceMapper,
                                 UserContextService userContextService,
                                 MeterRegistry registry) {
        super(userContextService);
        this.contentSourceDetailService = contentSourceDetailService;
        this.contentSourceMapper = contentSourceMapper;
        this.registry = registry;
    }

    @GET
    @RolesAllowed(Permission.V_SOURCE_VIEW)
    @Operation(summary = "Get all content sources", description = "Retrieves a paginated list of all content sources.")
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

        PanacheQuery<ContentSource> query;
        StringBuilder hql = new StringBuilder("from ContentSource s where 1=1");
        Map<String, Object> params = new HashMap<>();

        if (search != null && !search.isBlank()) {
            hql.append(" and (lower(s.sourceIdentifier) like :search or lower(s.name) like :search or lower(s.description) like :search)");
            params.put("search", "%" + search.toLowerCase() + "%");
        }

        if (excludeIds != null && !excludeIds.isEmpty()) {
            hql.append(" and s.id not in :excludeIds");
            params.put("excludeIds", excludeIds);
        }

        query = ContentSource.find(hql.toString(), Sort.by("id"), params);
        
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
    @Operation(summary = "Get content source by ID", description = "Retrieves details of a specific content source.")
    @APIResponse(
            responseCode = "200",
            description = "Content source retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Source not found")
    public ContentSourceDTO getContentSourceById(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));
        return contentSourceMapper.toDTO(entity);
    }

    @POST
    @Transactional
    @RolesAllowed(Permission.V_SOURCE_CREATE)
    @Operation(summary = "Create content source", description = "Creates a new content source.")
    @APIResponse(
            responseCode = "201",
            description = "Content source created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
    )
    public Response createContentSource(@Valid @NotNull ContentSourceCreateDTO createDTO) {
        SourceTypeEnum sourceType;
        try {
            sourceType = SourceTypeEnum.valueOf(createDTO.platform());
        } catch (IllegalArgumentException _) {
            throw new BadRequestException("Invalid platform: " + createDTO.platform());
        }

        // If it already exists, return the existing one as per user feedback
        Optional<ContentSource> existing = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2",
                createDTO.sourceIdentifier(), sourceType.getId()).firstResultOptional();
        
        if (existing.isPresent()) {
            ContentSource cs = existing.get();
            logger.info("ContentSource already exists for identifier: {}, platform: {}. Returning existing.", cs.sourceIdentifier, createDTO.platform());
            return Response.ok(contentSourceMapper.toDTO(cs))
                    .location(URI.create("/api/v1/sources/" + cs.id))
                    .type(MediaType.APPLICATION_JSON)
                    .build();
        }

        ContentSource contentSource;
        try {
            contentSource = contentSourceMapper.toEntity(createDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        if (contentSource.name == null || contentSource.name.isBlank()) {
            contentSource.name = contentSource.sourceIdentifier;
        }

        contentSource.persist();
        logger.info("Created new ContentSource: {} [{}] (id: {})", contentSource.name, contentSource.sourceIdentifier, contentSource.id);
        
        registry.counter("povzetkar_source_added_total", 
                Tags.of("platform", createDTO.platform()))
                .increment();

        contentSourceDetailService.enrichContentSource(contentSource);
        return Response.created(URI.create("/api/v1/sources/" + contentSource.id))
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
        return contentSourceDetailService.fetchContentSourceDetails(sourceTypeId, identifier)
                .orElseThrow(() -> new NotFoundException("Details for source type "
                        + SourceTypeEnum.fromId(sourceTypeId) + " with identifier " + identifier + NOT_FOUND));
    }

    private void checkForDuplicateContentSource(String sourceIdentifier, int sourceTypeId, Long excludeId) {
        Optional<ContentSource> existing = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2",
                sourceIdentifier, sourceTypeId).firstResultOptional();

        if (existing.isPresent() && (excludeId == null || !existing.get().id.equals(excludeId))) {
            throw new WebApplicationException(
                    "ContentSource with the same sourceIdentifier and platform already exists.",
                    Response.Status.CONFLICT);
        }
    }

}
