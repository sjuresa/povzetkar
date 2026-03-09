package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.mapper.ContentItemMapper;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;

@Path("/api/v1/sources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Content Items", description = "Endpoints for viewing Content Items under Sources")
public class SourceItemApiResource extends BaseApiResource {

    private final ContentItemMapper contentItemMapper;

    @Inject
    public SourceItemApiResource(ContentItemMapper contentItemMapper, UserContextService userContextService) {
        super(userContextService);
        this.contentItemMapper = contentItemMapper;
    }

    @GET
    @Path("/{id}/items")
    @RolesAllowed("SOURCE_VIEW")
    @Operation(summary = "Get items by source", description = "Retrieves a paginated list of content items for a specific source.")
    @APIResponse(
            responseCode = "200",
            description = "List of source items retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    @APIResponse(responseCode = "404", description = "Source not found")
    public PaginatedResponse<ContentItemDTO> getSourceItems(
            @PathParam("id") Long sourceId,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        getAuthenticatedUser();

        ContentSource source = ContentSource.findById(sourceId);
        if (source == null) {
            throw new NotFoundException("Source with id " + sourceId + " not found.");
        }

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        PanacheQuery<ContentItem> query = ContentItem.find("contentSource", Sort.by("publishedAt").descending(), source);
        query.page(pageIndex, clampedPageSize);

        List<ContentItemDTO> dtoList = query.stream().map(contentItemMapper::toDTO).toList();
        long count = query.count();
 
        return new PaginatedResponse<>(dtoList, count, pageIndex, clampedPageSize);
    }

}
