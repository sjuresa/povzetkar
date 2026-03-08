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
import io.quarkus.security.identity.SecurityIdentity;
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
    private final SecurityIdentity securityIdentity;

    @Inject
    public DashboardApiResource(UserContextService userContextService, BriefingMapper briefingMapper, SecurityIdentity securityIdentity) {
        super(userContextService);
        this.briefingMapper = briefingMapper;
        this.securityIdentity = securityIdentity;
    }

    @GET
    @RolesAllowed({"TOPIC_VIEW", "BRIEFING_VIEW"})
    @Operation(summary = "Get Dashboard Data", description = "Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests.")
    public DashboardDTO getDashboard() {
        User user = getAuthenticatedUser();

        long activeTopics = 0;
        if (securityIdentity.hasRole("TOPIC_VIEW")) {
            activeTopics = Topic.count("user = ?1 and enabled = true", user);
        }
        
        long weeklyBriefings = 0;
        List<BriefingSummaryDTO> recentBriefings = List.of();
        
        if (securityIdentity.hasRole("BRIEFING_VIEW")) {
            LocalDate sevenDaysAgo = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(6);
            weeklyBriefings = Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo);

            List<Briefing> recentEntities = Briefing.<Briefing>find("from Briefing b join fetch b.topic where b.topic.user = ?1 order by b.briefingDate desc", user)
                    .page(0, 5)
                    .list();

            recentBriefings = recentEntities.stream()
                    .map(briefingMapper::toSummaryDTO)
                    .toList();
        }

        DashboardStatsDTO stats = new DashboardStatsDTO(activeTopics, weeklyBriefings);
        return new DashboardDTO(recentBriefings, stats);
    }
}
