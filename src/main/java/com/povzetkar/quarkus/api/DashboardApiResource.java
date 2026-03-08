package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.DashboardDTO;
import com.povzetkar.quarkus.api.dto.DashboardStatsDTO;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.Blocking;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
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
@Tag(name = "Dashboard", description = "Endpoints for frontend dashboard aggregation")
public class DashboardApiResource extends BaseApiResource {
    private static final int BRIEFING_STATS_DAYS = 7;
    private static final int RECENT_BRIEFINGS_LIMIT = 5;

    private final BriefingMapper briefingMapper;
    private final SecurityIdentity securityIdentity;

    @Inject
    public DashboardApiResource(UserContextService userContextService, BriefingMapper briefingMapper, SecurityIdentity securityIdentity) {
        super(userContextService);
        this.briefingMapper = briefingMapper;
        this.securityIdentity = securityIdentity;
    }

    @GET
    @Blocking
    @RolesAllowed({"TOPIC_VIEW", "BRIEFING_VIEW"})
    @Operation(summary = "Get Dashboard Data", description = "Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests.")
    public Uni<DashboardDTO> getDashboard() {
        User user = getAuthenticatedUser();
        boolean hasTopicView = securityIdentity.hasRole("TOPIC_VIEW");
        boolean hasBriefingView = securityIdentity.hasRole("BRIEFING_VIEW");

        Uni<Long> activeTopicsUni = hasTopicView
                ? Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(() -> Topic.count("user = ?1 and enabled = true", user)))
                        .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                : Uni.createFrom().item(0L);

        Uni<Long> briefingsLast7DaysUni = hasBriefingView
                ? Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(() -> {
                    LocalDate sevenDaysAgo = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(BRIEFING_STATS_DAYS - 1L);
                    return Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo);
                })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                : Uni.createFrom().item(0L);

        Uni<List<BriefingSummaryDTO>> recentBriefingsUni = hasBriefingView
                ? Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(() -> {
                    List<Briefing> recentEntities = Briefing.<Briefing>find("from Briefing b join fetch b.topic where b.topic.user = ?1 order by b.briefingDate desc", user)
                            .page(0, RECENT_BRIEFINGS_LIMIT)
                            .list();
                    return recentEntities.stream()
                            .map(briefingMapper::toSummaryDTO)
                            .toList();
                })).runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                : Uni.createFrom().item(List.of());

        return Uni.combine().all().unis(activeTopicsUni, briefingsLast7DaysUni, recentBriefingsUni)
                .asTuple()
                .map(tuple -> {
                    long activeTopics = tuple.getItem1();
                    long briefingsLast7Days = tuple.getItem2();
                    List<BriefingSummaryDTO> recentBriefings = tuple.getItem3();

                    DashboardStatsDTO stats = new DashboardStatsDTO(activeTopics, briefingsLast7Days);
                    return new DashboardDTO(recentBriefings, stats);
                });
    }
}
