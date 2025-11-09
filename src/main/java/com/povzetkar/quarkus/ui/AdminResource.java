package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.OrchestrationManager;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
@Blocking
public class AdminResource {

    private final Template admin;
    private final ContentSourceDetailService contentSourceDetailService;
    private final OrchestrationManager orchestrationManager;

    @ConfigProperty(name = "povzetkar.ui.datetime.format")
    String dateTimeFormat;

    @Inject
    public AdminResource(@Location("admin.html") Template admin,
                         ContentSourceDetailService contentSourceDetailService,
                         OrchestrationManager orchestrationManager) {
        this.admin = admin;
        this.contentSourceDetailService = contentSourceDetailService;
        this.orchestrationManager = orchestrationManager;
    }

    @Route(path = "/ui/admin", methods = Route.HttpMethod.GET)
    @Blocking
    public void getAdminPage(RoutingContext context) {
        String successMessage = context.request().getParam("success");
        String errorMessage = context.request().getParam("error");

        Map<String, Object> data = new HashMap<>();
        data.put("successMessage", successMessage);
        data.put("errorMessage", errorMessage);
        data.put("dateTimeFormat", dateTimeFormat);

        // Fetch pending content items
        List<ContentItem> pendingContentItems = ContentItem.find(
                "processingStatus = ?1 ORDER BY createdAt DESC", ProcessingStatus.PENDING
        ).page(0, 25).list();
        data.put("pendingContentItems", pendingContentItems);

        // Fetch recent summaries with their content items
        List<Summary> recentSummaries = Summary.find(
                "SELECT s FROM Summary s JOIN FETCH s.contentItem ORDER BY s.createdAt DESC"
        ).page(0, 25).list();
        data.put("recentSummaries", recentSummaries);

        // Fetch recent briefings
        List<Briefing> recentBriefings = Briefing.findAll(Sort.by("createdAt", Sort.Direction.Descending)).page(0, 25).list();
        data.put("recentBriefings", recentBriefings);

        // Fetch failed content items
        List<ContentItem> failedContentItems = ContentItem.find(
                "processingStatus = ?1 OR processingStatus = ?2 ORDER BY createdAt DESC",
                ProcessingStatus.FAILED, ProcessingStatus.RETRY_LATER
        ).page(0, 25).list();
        data.put("failedContentItems", failedContentItems);


        context.response().end(admin.instance().data(data).render());
    }

    @Route(path = "/ui/admin/enrich-sources", methods = Route.HttpMethod.POST)
    @Blocking
    public void enrichSources(RoutingContext context) {
        try {
            contentSourceDetailService.enrichContentSources();
            context.response()
                    .putHeader("Location", "/ui/admin?success=Content sources enrichment started successfully.")
                    .setStatusCode(303)
                    .end();
        } catch (Exception e) {
            context.response()
                    .putHeader("Location", "/ui/admin?error=Failed to start content sources enrichment: " + e.getMessage())
                    .setStatusCode(303)
                    .end();
        }
    }

    @Route(path = "/ui/admin/run-orchestration", methods = Route.HttpMethod.POST)
    @Blocking
    public void runOrchestration(RoutingContext context) {
        String startDateParam = context.request().getParam("startDate");
        String endDateParam = context.request().getParam("endDate");

        LocalDate startDate = null;
        LocalDate endDate = null;
        String errorMessage = null;

        try {
            if (startDateParam != null && !startDateParam.isEmpty()) {
                startDate = LocalDate.parse(startDateParam);
            }
            if (endDateParam != null && !endDateParam.isEmpty()) {
                endDate = LocalDate.parse(endDateParam);
            }

            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                errorMessage = "End date cannot be before start date.";
            } else if (startDate != null && startDate.isAfter(LocalDate.now())) {
                errorMessage = "Start date cannot be in the future.";
            } else if (endDate != null && endDate.isAfter(LocalDate.now())) {
                errorMessage = "End date cannot be in the future.";
            }

            if (errorMessage != null) {
                context.response()
                        .putHeader("Location", "/ui/admin?error=" + errorMessage)
                        .setStatusCode(303)
                        .end();
                return;
            }

            orchestrationManager.runOrchestrationManually(startDate, endDate)
                    .subscribe().with(
                            success -> context.response()
                                    .putHeader("Location", "/ui/admin?success=Orchestration process started successfully.")
                                    .setStatusCode(303)
                                    .end(),
                            failure -> context.response()
                                    .putHeader("Location", "/ui/admin?error=Failed to start orchestration process: " + failure.getMessage())
                                    .setStatusCode(303)
                                    .end()
                    );

        } catch (DateTimeParseException e) {
            context.response()
                    .putHeader("Location", "/ui/admin?error=Invalid date format. Please use YYYY-MM-DD.")
                    .setStatusCode(303)
                    .end();
        } catch (Exception e) {
            context.response()
                    .putHeader("Location", "/ui/admin?error=Failed to start orchestration process: " + e.getMessage())
                    .setStatusCode(303)
                    .end();
        }
    }
}
