package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.DashboardDTO;
import com.povzetkar.quarkus.api.dto.DashboardStatsDTO;
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
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.time.LocalDate;
import java.util.List;
import java.util.function.Supplier;

@Path("/api/v1/dashboard")
@ApplicationScoped
@Produces(MediaType.APPLICATION_JSON)
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
    public Uni<DashboardDTO> getDashboard() {
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(this::getAuthenticatedUser))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .emitOn(Infrastructure.getDefaultExecutor())
                .flatMap(this::fetchDashboardData);
    }

    private Uni<DashboardDTO> fetchDashboardData(User user) {
        boolean hasTopicView = securityIdentity.hasRole(Permission.TOPIC_VIEW.name());
        boolean hasBriefingView = securityIdentity.hasRole(Permission.BRIEFING_VIEW.name());

        Uni<Long> activeTopicsUni = getActiveTopicsUni(user, hasTopicView);
        Uni<Long> briefingsLast7DaysUni = getBriefingStatsUni(user, hasBriefingView);
        Uni<List<BriefingSummaryDTO>> recentBriefingsUni = getRecentBriefingsUni(user, hasBriefingView);

        return Uni.combine().all().unis(activeTopicsUni, briefingsLast7DaysUni, recentBriefingsUni)
                .with(this::assembleDashboard)
                .emitOn(Infrastructure.getDefaultExecutor());
    }

    private Uni<Long> getActiveTopicsUni(User user, boolean hasPermission) {
        return executeInTransactionIf(
                hasPermission,
                () -> Topic.count("user = ?1 and enabled = true", user),
                0L
        );
    }

    private Uni<Long> getBriefingStatsUni(User user, boolean hasPermission) {
        final LocalDate sevenDaysAgo = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(BRIEFING_STATS_DAYS - 1L);
        return executeInTransactionIf(
                hasPermission,
                () -> Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo),
                0L
        );
    }

    private Uni<List<BriefingSummaryDTO>> getRecentBriefingsUni(User user, boolean hasPermission) {
        return executeInTransactionIf(
                hasPermission,
                () -> Briefing.getEntityManager().createQuery(
                                "select new com.povzetkar.quarkus.api.dto.BriefingSummaryDTO(b.id, b.topic.id, b.topic.name, b.title, b.briefingDate) " +
                                "from Briefing b where b.topic.user = ?1 order by b.briefingDate desc",
                                BriefingSummaryDTO.class)
                        .setParameter(1, user)
                        .setFirstResult(0)
                        .setMaxResults(RECENT_BRIEFINGS_LIMIT)
                        .getResultList(),
                List.of()
        );
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
