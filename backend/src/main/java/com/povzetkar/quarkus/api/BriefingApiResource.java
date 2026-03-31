package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.dto.BriefingPaginatedResponse;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.api.util.FilterBuilder;
import com.povzetkar.quarkus.api.util.QueryParser;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.enums.ParameterIn;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@Path("/api/v1/briefings")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Briefings", description = "Endpoints for viewing Briefings")
public class BriefingApiResource extends BaseApiResource {

    private final BriefingMapper briefingMapper;

    @Inject
    public BriefingApiResource(BriefingMapper briefingMapper, UserContextService userContextService) {
        super(userContextService);
        this.briefingMapper = briefingMapper;
    }

    @GET
    @RolesAllowed(Permission.V_BRIEFING_VIEW)
    @Operation(summary = "Get all briefings", description = "Retrieves a paginated list of briefings for the current user.")
    @APIResponse(
            responseCode = "200",
            description = "List of briefings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingPaginatedResponse.class))
    )
    @Parameter(name = "search", description = "Keyword search across briefing title and executive summary")
    @Parameter(name = "sourceId", description = "Filter briefings containing summaries from a specific content source")
    @Transactional
    public BriefingPaginatedResponse getBriefings(
            @QueryParam("topicId") Long topicId,
            @QueryParam("sourceId") Long sourceId,
            @QueryParam("search") @DefaultValue("") String search,
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate,
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        User user = getAuthenticatedUser();

        FilterBuilder filterBuilder = new FilterBuilder("topic.user = :user")
            .withParam("user", user);

        filterBuilder.and("topic.id = :topicId", "topicId", topicId);
        filterBuilder.and("briefingDate >= :startDate", "startDate", startDate);
        filterBuilder.and("briefingDate <= :endDate", "endDate", endDate);

        if (sourceId != null) {
            filterBuilder.and(
                    "EXISTS (SELECT 1 FROM Summary s WHERE s MEMBER OF summaries AND s.contentItem.contentSource.id = :sourceId)",
                    "sourceId", sourceId);
        }

        if (search != null && !search.isBlank()) {
            String escapedSearch = search.toLowerCase()
                .replace("!", "!!")
                .replace("%", "!%")
                .replace("_", "!_");
            filterBuilder.and(
                    "(lower(title) LIKE :search ESCAPE '!' OR lower(metaSummary) LIKE :search ESCAPE '!')",
                    "search", "%" + escapedSearch + "%");
        }

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        Sort panacheSort = QueryParser.parseSort(sort, Sort.by("briefingDate").descending(), Set.of("id", "briefingDate", "topic.id", "title"));
        PanacheQuery<Briefing> query = Briefing.find(filterBuilder.getQuery(), panacheSort, filterBuilder.getParams());
        long count = query.count();
        List<BriefingSummaryDTO> dtoList = query.page(pageIndex, clampedPageSize)
                .stream()
                .map(briefingMapper::toSummaryDTO)
                .toList();

        return new BriefingPaginatedResponse(dtoList, count, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed(Permission.V_BRIEFING_VIEW)
    @Operation(summary = "Get briefing by ID", description = "Retrieves a specific briefing by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Briefing details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingDetailDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Briefing not found")
    @Parameter(name = "id", in = ParameterIn.PATH, required = true, description = "The ID of the briefing to retrieve", schema = @Schema(type = SchemaType.INTEGER))
    public BriefingDetailDTO getBriefing(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Briefing briefing = Briefing.<Briefing>find(
                "select distinct b from Briefing b left join fetch b.summaries s left join fetch s.contentItem where b.id = ?1 and b.topic.user = ?2",
                id, user)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Briefing with id " + id + " not found."));

        return briefingMapper.toDetailDTO(briefing);
    }

    @POST
    @Path("/{id}/share")
    @Transactional
    @RolesAllowed(Permission.V_BRIEFING_VIEW)
    @Operation(summary = "Enable sharing for a briefing", description = "Generates a public UUID for the briefing to enable unauthenticated access.")
    @APIResponse(responseCode = "200", description = "Sharing enabled successfully", content = @Content(mediaType = "application/json", schema = @Schema(implementation = String.class)))
    @APIResponse(responseCode = "404", description = "Briefing not found")
    public String shareBriefing(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Briefing briefing = Briefing.<Briefing>find("id = ?1 and topic.user = ?2", id, user)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Briefing not found."));
        
        briefing.share();
        return briefing.uuid;
    }

    @POST
    @Path("/{id}/unshare")
    @Transactional
    @RolesAllowed(Permission.V_BRIEFING_VIEW)
    @Operation(summary = "Disable sharing for a briefing", description = "Removes the public UUID for the briefing to disable unauthenticated access.")
    @APIResponse(responseCode = "204", description = "Sharing disabled successfully")
    @APIResponse(responseCode = "404", description = "Briefing not found")
    public void unshareBriefing(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Briefing briefing = Briefing.<Briefing>find("id = ?1 and topic.user = ?2", id, user)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Briefing not found."));
        
        briefing.unshare();
    }
}
