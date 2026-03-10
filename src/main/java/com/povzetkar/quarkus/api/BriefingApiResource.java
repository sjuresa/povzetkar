package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.PaginatedResponse;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.User;
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

import java.time.LocalDate;
import java.util.List;

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
    @RolesAllowed("BRIEFING_VIEW")
    @Operation(summary = "Get all briefings", description = "Retrieves a paginated list of briefings for the current user.")
    @APIResponse(
            responseCode = "200",
            description = "List of briefings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = PaginatedResponse.class))
    )
    public PaginatedResponse<BriefingSummaryDTO> getBriefings(
            @QueryParam("topicId") Long topicId,
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate,
            @QueryParam("sort") List<String> sort,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        User user = getAuthenticatedUser();

        com.povzetkar.quarkus.api.util.FilterBuilder filterBuilder = new com.povzetkar.quarkus.api.util.FilterBuilder("topic.user = :user");
        filterBuilder.getParams().put("user", user);

        filterBuilder.and("topic.id = :topicId", "topicId", topicId);
        filterBuilder.and("briefingDate >= :startDate", "startDate", startDate);
        filterBuilder.and("briefingDate <= :endDate", "endDate", endDate);

        final int clampedPageSize = Math.clamp(pageSize, 1, ApiConstants.MAX_PAGE_SIZE);
        io.quarkus.panache.common.Sort panacheSort = com.povzetkar.quarkus.api.util.QueryParser.parseSort(sort, Sort.by("briefingDate").descending());
        PanacheQuery<Briefing> query = Briefing.find(filterBuilder.getQuery(), panacheSort, filterBuilder.getParams());
        long count = query.count();
        List<BriefingSummaryDTO> dtoList = query.page(pageIndex, clampedPageSize)
                .stream()
                .map(briefingMapper::toSummaryDTO)
                .toList();
 
        return new PaginatedResponse<>(dtoList, count, pageIndex, clampedPageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("BRIEFING_VIEW")
    @Operation(summary = "Get briefing by ID", description = "Retrieves a specific briefing by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Briefing details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingDetailDTO.class))
    )
    @APIResponse(responseCode = "404", description = "Briefing not found")
    public BriefingDetailDTO getBriefing(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Briefing briefing = Briefing.<Briefing>find(
                "select distinct b from Briefing b left join fetch b.summaries s left join fetch s.contentItem where b.id = ?1 and b.topic.user = ?2",
                id, user)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Briefing with id " + id + " not found."));

        return briefingMapper.toDetailDTO(briefing);
    }
}
