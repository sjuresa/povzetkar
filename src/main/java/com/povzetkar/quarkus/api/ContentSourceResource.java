package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.dto.ContentSourceDTO;
import com.povzetkar.quarkus.api.dto.ContentSourcePatchDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.mapper.ContentSourceMapper;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
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
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/sources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Sources", description = "Endpoints for managing Content Sources")
public class ContentSourceResource extends BaseApiResource {

    public static final String CONTENT_SOURCE_WITH_ID = "ContentSource with id ";
    public static final String NOT_FOUND = " not found";
    private final ContentSourceDetailService contentSourceDetailService;
    private final ContentSourceMapper contentSourceMapper;

    @Inject
    public ContentSourceResource(ContentSourceDetailService contentSourceDetailService,
                                 ContentSourceMapper contentSourceMapper,
                                 UserContextService userContextService) {
        super(userContextService);
        this.contentSourceDetailService = contentSourceDetailService;
        this.contentSourceMapper = contentSourceMapper;
    }

    @GET
    @RolesAllowed("SOURCE_VIEW")
    @Operation(summary = "Get all content sources", description = "Retrieves a paginated list of all content sources.")
    @APIResponse(
            responseCode = "200",
            description = "List of content sources retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<ContentSourceDTO> getAllContentSources(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        PanacheQuery<ContentSource> query = ContentSource.findAll(Sort.by("id"));
        query.page(pageIndex, pageSize);

        List<ContentSourceDTO> dtoList = query.stream().map(contentSourceMapper::toDTO).toList();
        long count = query.count();
 
        return new PaginatedResponse<>(dtoList, count);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("SOURCE_VIEW")
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
    @RolesAllowed("SOURCE_CREATE")
    @Operation(summary = "Create content source", description = "Creates a new content source.")
    @APIResponse(
            responseCode = "201",
            description = "Content source created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
    )
    public Response createContentSource(@Valid @NotNull ContentSourceCreateDTO createDTO) {
        ContentSource contentSource;
        try {
            contentSource = contentSourceMapper.toEntity(createDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        checkForDuplicateContentSource(contentSource.sourceIdentifier, contentSource.sourceTypeId, null);

        contentSource.persist();
        return Response.created(URI.create("/api/v1/sources/" + contentSource.id))
                .entity(contentSourceMapper.toDTO(contentSource))
                .build();
    }


    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed("SOURCE_UPDATE")
    @Operation(summary = "Partially update content source", description = "Partially updates an existing content source.")
    @APIResponse(
           responseCode = "200",
           description = "Content source patched successfully",
           content = @Content(mediaType = "application/json", schema = @Schema(implementation = ContentSourceDTO.class))
   )
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
    @RolesAllowed("SOURCE_DELETE")
    @Operation(summary = "Delete content source", description = "Deletes a specific content source.")
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
    @RolesAllowed("ADMIN_SYSTEM_TRIGGER")
    public Response enrichContentSources() {
        contentSourceDetailService.enrichContentSources();
        return Response.accepted().entity("Content sources enrichment started.").build();
    }

    @GET
    @Path("/details/{sourceTypeId}/{identifier}")
    @RolesAllowed("SOURCE_VIEW")
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
