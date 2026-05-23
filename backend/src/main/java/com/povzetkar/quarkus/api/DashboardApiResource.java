package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.DashboardDTO;
import com.povzetkar.quarkus.api.dto.DashboardStatsDTO;
import com.povzetkar.quarkus.api.filter.Enveloped;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.security.Permission;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.annotation.security.RolesAllowed;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.media.Content;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.responses.APIResponse;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.function.Supplier;

@Path("/api/v1/dashboard")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
@Enveloped
@Tag(name = "Dashboard", description = "Endpoints for frontend dashboard aggregation")
public class DashboardApiResource extends BaseApiResource {
    private static final int BRIEFING_STATS_DAYS = 7;
    private static final int RECENT_BRIEFINGS_LIMIT = 5;

    private final SecurityIdentity securityIdentity;

    @Inject
    public DashboardApiResource(UserContextService userContextService, SecurityIdentity securityIdentity) {
        super(userContextService);
        this.securityIdentity = securityIdentity;
    }

    @GET
    @RolesAllowed({Permission.V_TOPIC_VIEW, Permission.V_BRIEFING_VIEW})
    @Operation(summary = "Get Dashboard Data", description = "Aggregated convenience endpoint for the frontend dashboard to prevent N+1 requests.")
    @APIResponse(
            responseCode = "200",
            description = "Dashboard data retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = DashboardDTO.class))
    )
    public Uni<DashboardDTO> getDashboard() {
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(this::getContentViewingUser))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .flatMap(this::fetchDashboardData);
    }

    private Uni<DashboardDTO> fetchDashboardData(User user) {
        Uni<Long> activeTopicsUni = executeInTransactionIf(
                securityIdentity.hasRole(Permission.TOPIC_VIEW.name()),
                () -> Topic.count("user = ?1 and enabled = true", user),
                0L
        ).onFailure().recoverWithItem(0L);

        final LocalDate sevenDaysAgo = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(BRIEFING_STATS_DAYS - 1L);
        Uni<Long> briefingsLast7DaysUni = executeInTransactionIf(
                securityIdentity.hasRole(Permission.BRIEFING_VIEW.name()),
                () -> Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo),
                0L
        ).onFailure().recoverWithItem(0L);

        Uni<List<BriefingSummaryDTO>> recentBriefingsUni = this.<List<BriefingSummaryDTO>>executeInTransactionIf(
                securityIdentity.hasRole(Permission.BRIEFING_VIEW.name()),
                () -> Briefing.findRecentBriefingSummaries(user, RECENT_BRIEFINGS_LIMIT),
                Collections.emptyList()
        ).onFailure().recoverWithItem(Collections.emptyList());

        return Uni.combine().all().unis(activeTopicsUni, briefingsLast7DaysUni, recentBriefingsUni)
                .with(this::assembleDashboard)
                .emitOn(Infrastructure.getDefaultExecutor());
    }

    private DashboardDTO assembleDashboard(Long activeTopics, Long briefingsLast7Days, List<BriefingSummaryDTO> recentBriefings) {
        DashboardStatsDTO stats = new DashboardStatsDTO(activeTopics, briefingsLast7Days);
        return new DashboardDTO(recentBriefings, stats);
    }

    private <T> Uni<T> executeInTransactionIf(boolean hasPermission, Supplier<T> supplier, T defaultValue) {
        if (!hasPermission) {
            return Uni.createFrom().item(defaultValue);
        }
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(supplier::get))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }

}
