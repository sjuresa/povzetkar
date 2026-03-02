package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.*;
import com.povzetkar.quarkus.api.mapper.ContentSourceMapper;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import jakarta.annotation.security.RolesAllowed;

import java.net.URI;
import java.util.List;
import java.util.Optional;

@Path("/api/v1/sources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Sources", description = "Endpoints for managing Content Sources")
public class ContentSourceResource {

    public static final String CONTENT_SOURCE_WITH_ID = "ContentSource with id ";
    public static final String NOT_FOUND = " not found";
    private final ContentSourceDetailService contentSourceDetailService;
    private final ContentSourceMapper contentSourceMapper;

    @Inject
    public ContentSourceResource(ContentSourceDetailService contentSourceDetailService,
            ContentSourceMapper contentSourceMapper) {
        this.contentSourceDetailService = contentSourceDetailService;
        this.contentSourceMapper = contentSourceMapper;
    }

    @GET
    @RolesAllowed("SOURCE_VIEW")
    @Operation(summary = "Get all content sources", description = "Retrieves a paginated list of all content sources.")
    public PaginatedResponse<ContentSourceDTO> getAllContentSources(
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        PanacheQuery<ContentSource> query = ContentSource.findAll(Sort.by("id"));
        query.page(pageIndex, pageSize);

        List<ContentSourceDTO> dtoList = query.list().stream().map(contentSourceMapper::toDTO).toList();
        long count = query.count();

        return new PaginatedResponse<>(dtoList, count);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("SOURCE_VIEW")
    @Operation(summary = "Get content source by ID", description = "Retrieves a specific content source by its ID.")
    public ContentSourceDTO getContentSourceById(@PathParam("id") Long id) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));
        return contentSourceMapper.toDTO(entity);
    }

    @POST
    @Transactional
    @RolesAllowed("SOURCE_CREATE")
    @Operation(summary = "Create content source", description = "Creates a new content source.")
    public Response createContentSource(@Valid @NotNull ContentSourceCreateDTO createDTO) {
        ContentSource contentSource;
        try {
            contentSource = contentSourceMapper.toEntity(createDTO);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        Optional<ContentSource> existing = ContentSource.find("sourceIdentifier = ?1 and sourceTypeId = ?2",
                contentSource.sourceIdentifier, contentSource.sourceTypeId).firstResultOptional();

        if (existing.isPresent()) {
            throw new WebApplicationException(
                    "ContentSource with the same sourceIdentifier and platform already exists.",
                    Response.Status.CONFLICT);
        }

        contentSource.persist();
        return Response.created(URI.create("/api/v1/sources/" + contentSource.id))
                .entity(contentSourceMapper.toDTO(contentSource)).build();
    }

    @PUT
    @Path("/{id}")
    @Transactional
    @RolesAllowed("SOURCE_UPDATE")
    @Operation(summary = "Update content source", description = "Fully replaces an existing content source.")
    public ContentSourceDTO updateContentSource(@PathParam("id") Long id,
            @Valid @NotNull ContentSourceUpdateDTO updateDTO) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));

        Integer parsedSourceTypeId;
        try {
            parsedSourceTypeId = contentSourceMapper.mapPlatformToSourceTypeId(updateDTO.platform());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        if (parsedSourceTypeId == null) {
            throw new BadRequestException("Platform must not be blank");
        }

        Optional<ContentSource> existingWithSameKey = ContentSource
                .find("sourceIdentifier = ?1 and sourceTypeId = ?2 and id != ?3",
                        updateDTO.sourceIdentifier(), parsedSourceTypeId, id)
                .firstResultOptional();

        if (existingWithSameKey.isPresent()) {
            throw new WebApplicationException(
                    "Another ContentSource with the same sourceIdentifier and platform already exists.",
                    Response.Status.CONFLICT);
        }

        try {
            contentSourceMapper.updateEntity(updateDTO, entity);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }

        return contentSourceMapper.toDTO(entity);
    }

    @PATCH
    @Path("/{id}")
    @Transactional
    @RolesAllowed("SOURCE_UPDATE")
    @Operation(summary = "Partially update content source", description = "Partially updates an existing content source.")
    public ContentSourceDTO patchContentSource(@PathParam("id") Long id,
            @Valid @NotNull ContentSourcePatchDTO patchDTO) {
        ContentSource entity = ContentSource.<ContentSource>findByIdOptional(id)
                .orElseThrow(() -> new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND));

        contentSourceMapper.patchEntity(patchDTO, entity);
        return contentSourceMapper.toDTO(entity);
    }

    @DELETE
    @Path("/{id}")
    @Transactional
    @RolesAllowed("SOURCE_DELETE")
    @Operation(summary = "Delete content source", description = "Deletes a specific content source.")
    public Response deleteContentSource(@PathParam("id") Long id) {
        if (!ContentSource.deleteById(id)) {
            throw new NotFoundException(CONTENT_SOURCE_WITH_ID + id + NOT_FOUND);
        }
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
}
