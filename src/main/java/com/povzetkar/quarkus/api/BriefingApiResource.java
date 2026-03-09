package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.BriefingDetailDTO;
import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Parameters;
import io.quarkus.panache.common.Sort;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotAuthorizedException;
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
@Tag(name = "Briefings", description = "Endpoints for viewing Briefings")
public class BriefingApiResource {

    private final BriefingMapper briefingMapper;
    private final UserContextService userContextService;

    @Inject
    public BriefingApiResource(BriefingMapper briefingMapper, UserContextService userContextService) {
        this.briefingMapper = briefingMapper;
        this.userContextService = userContextService;
    }

    @GET
    @RolesAllowed("BRIEFING_VIEW")
    @Operation(summary = "Get all briefings", description = "Retrieves a paginated list of briefings for the current user.")
    @APIResponse(
            responseCode = "200",
            description = "List of briefings retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingListEnvelope.class))
    )
    public ApiEnvelopeDTO<List<BriefingSummaryDTO>> getBriefings(
            @QueryParam("topicId") Long topicId,
            @QueryParam("startDate") LocalDate startDate,
            @QueryParam("endDate") LocalDate endDate,
            @QueryParam("page") @DefaultValue("0") int pageIndex,
            @QueryParam("size") @DefaultValue("20") int pageSize) {

        User user = getAuthenticatedUser();

        StringBuilder queryBuilder = new StringBuilder("topic.user = :user");
        Parameters params = Parameters.with("user", user);

        if (topicId != null) {
            queryBuilder.append(" and topic.id = :topicId");
            params.and("topicId", topicId);
        }
        if (startDate != null) {
            queryBuilder.append(" and briefingDate >= :startDate");
            params.and("startDate", startDate);
        }
        if (endDate != null) {
            queryBuilder.append(" and briefingDate <= :endDate");
            params.and("endDate", endDate);
        }

        PanacheQuery<Briefing> query = Briefing.find(queryBuilder.toString(), Sort.by("briefingDate").descending(), params);
        query.page(pageIndex, pageSize);

        List<BriefingSummaryDTO> dtoList = query.stream().map(briefingMapper::toSummaryDTO).toList();
        long count = query.count();

        return ApiEnvelopeDTO.paginated(dtoList, count, pageIndex, pageSize);
    }

    @GET
    @Path("/{id}")
    @RolesAllowed("BRIEFING_VIEW")
    @Operation(summary = "Get briefing by ID", description = "Retrieves a specific briefing by its ID.")
    @APIResponse(
            responseCode = "200",
            description = "Briefing details retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = BriefingEnvelope.class))
    )
    @APIResponse(responseCode = "404", description = "Briefing not found")
    public ApiEnvelopeDTO<BriefingDetailDTO> getBriefing(@PathParam("id") Long id) {
        User user = getAuthenticatedUser();
        Briefing briefing = Briefing.<Briefing>find(
                "select distinct b from Briefing b left join fetch b.summaries s left join fetch s.contentItem where b.id = ?1 and b.topic.user = ?2",
                id, user)
                .firstResultOptional()
                .orElseThrow(() -> new NotFoundException("Briefing with id " + id + " not found."));

        return ApiEnvelopeDTO.success(briefingMapper.toDetailDTO(briefing));
    }

    private User getAuthenticatedUser() {
        return userContextService.getCurrentUser()
                .orElseThrow(() -> new NotAuthorizedException("User must be authenticated"));
    }

    // Helper classes for OpenAPI documentation
    private static class BriefingEnvelope extends ApiEnvelopeDTO<BriefingDetailDTO> {}
    private static class BriefingListEnvelope extends ApiEnvelopeDTO<List<BriefingSummaryDTO>> {}
}
