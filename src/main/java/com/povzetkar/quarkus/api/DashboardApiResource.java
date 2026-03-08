package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.BriefingSummaryDTO;
import com.povzetkar.quarkus.api.dto.DashboardDTO;
import com.povzetkar.quarkus.api.dto.DashboardStatsDTO;
import com.povzetkar.quarkus.api.mapper.BriefingMapper;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Topic;
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
    public Uni<DashboardDTO> getDashboard() {
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(this::getAuthenticatedUser))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool())
                .emitOn(Infrastructure.getDefaultExecutor())
                .flatMap(user -> {
                    boolean hasTopicView = securityIdentity.hasRole("TOPIC_VIEW");
                    boolean hasBriefingView = securityIdentity.hasRole("BRIEFING_VIEW");

                    Uni<Long> activeTopicsUni = executeInTransactionIf(
                            hasTopicView,
                            () -> Topic.count("user = ?1 and enabled = true", user),
                            0L
                    );

                    Uni<Long> briefingsLast7DaysUni = executeInTransactionIf(
                            hasBriefingView,
                            () -> {
                                LocalDate sevenDaysAgo = LocalDate.now(java.time.ZoneOffset.UTC).minusDays(BRIEFING_STATS_DAYS - 1L);
                                return Briefing.count("topic.user = ?1 and briefingDate >= ?2", user, sevenDaysAgo);
                            },
                            0L
                    );

                    Uni<List<BriefingSummaryDTO>> recentBriefingsUni = executeInTransactionIf(
                            hasBriefingView,
                            () -> Briefing.<Briefing>find("from Briefing b join fetch b.topic where b.topic.user = ?1 order by b.briefingDate desc", user)
                                    .page(0, RECENT_BRIEFINGS_LIMIT)
                                    .list(),
                            List.<Briefing>of()
                    ).emitOn(Infrastructure.getDefaultExecutor())
                    .map(recentEntities ->
                            recentEntities.stream()
                                    .map(briefingMapper::toSummaryDTO)
                                    .toList()
                    );

                    return Uni.combine().all().unis(activeTopicsUni, briefingsLast7DaysUni, recentBriefingsUni)
                            .asTuple()
                            .emitOn(Infrastructure.getDefaultExecutor())
                            .map(tuple -> {
                                long activeTopics = tuple.getItem1();
                                long briefingsLast7Days = tuple.getItem2();
                                List<BriefingSummaryDTO> recentBriefings = tuple.getItem3();

                                DashboardStatsDTO stats = new DashboardStatsDTO(activeTopics, briefingsLast7Days);
                                return new DashboardDTO(recentBriefings, stats);
                            });
                });
    }

    private <T> Uni<T> executeInTransactionIf(boolean hasPermission, Supplier<T> supplier, T defaultValue) {
        if (!hasPermission) {
            return Uni.createFrom().item(defaultValue);
        }
        return Uni.createFrom().item(() -> QuarkusTransaction.requiringNew().call(supplier::get))
                .runSubscriptionOn(Infrastructure.getDefaultWorkerPool());
    }
}
