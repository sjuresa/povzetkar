package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentItemDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.ContentItemMapper;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.security.Permission;
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

import java.util.HashSet;
import java.util.List;
import java.util.Set;
 
@Path("/api/v1/sources")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
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
    @RolesAllowed(Permission.V_SOURCE_VIEW)
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
        Sort sort = Sort.by("publishedAt").descending();

        // Pattern inspired by ContentItemApiResource to solve ResultSet issues and N+1
        PanacheQuery<Long> idQuery = ContentItem.find("SELECT c.id FROM ContentItem c WHERE c.contentSource = ?1", sort, source).project(Long.class);
        long count = idQuery.count();
        List<Long> itemIds = idQuery.page(pageIndex, clampedPageSize).list();

        if (itemIds.isEmpty()) {
            return new PaginatedResponse<>(List.of(), count, pageIndex, clampedPageSize);
        }

        // Fetch items by ID list (using JOIN FETCH to be safe/consistent)
        List<ContentItem> items = ContentItem.list("FROM ContentItem c LEFT JOIN FETCH c.contentSource cs LEFT JOIN FETCH cs.topics WHERE c.id IN ?1", sort, itemIds);

        // Fetch all summary existence flags in one go
        final Set<Long> itemIdsWithSummaries = new HashSet<>(
            io.quarkus.hibernate.orm.panache.Panache.getEntityManager()
                   .createQuery("SELECT s.contentItem.id FROM Summary s WHERE s.contentItem.id IN :ids", Long.class)
                   .setParameter("ids", itemIds)
                   .getResultList()
        );

        List<ContentItemDTO> dtoList = items.stream()
                .map(item -> contentItemMapper.toDTO(item, itemIdsWithSummaries))
                .toList();
 
        return new PaginatedResponse<>(dtoList, count, pageIndex, clampedPageSize);
    }

}
