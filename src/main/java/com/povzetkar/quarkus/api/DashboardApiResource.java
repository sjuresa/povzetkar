package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.DashboardDTO;
import com.povzetkar.quarkus.api.dto.DashboardStatsDTO;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;

@Path("/api/v1/dashboard")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@Tag(name = "Dashboard", description = "Endpoints for frontend dashboard aggregation")
public class DashboardApiResource extends BaseApiResource {

    private final BriefingMapper briefingMapper;

    @Inject
    public DashboardApiResource(UserContextService userContextService, BriefingMapper briefingMapper) {
        super(userContextService);
        this.briefingMapper = briefingMapper;
    }

    @GET
    @RolesAllowed({"TOPIC_VIEW", "BRIEFING_VIEW"})
    @Operation(summary = "Get Dashboard Data", description = "Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests.")
    public DashboardDTO getDashboard() {
        User user = getAuthenticatedUser();

        long activeTopics = Topic.count("user = ?1 and enabled = true", user);
        
        LocalDate sevenDaysAgo = LocalDate.now().minusDays(7);
        long weeklyBriefings = Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo);

        List<Briefing> recentEntities = Briefing.<Briefing>find("topic.user = ?1 order by briefingDate desc", user)
                .page(0, 5)
                .list();

        List<BriefingSummaryDTO> recentBriefings = recentEntities.stream()
                .map(briefingMapper::toSummaryDTO)
                .toList();

        DashboardStatsDTO stats = new DashboardStatsDTO(activeTopics, weeklyBriefings);
        return new DashboardDTO(recentBriefings, stats);
    }
}
