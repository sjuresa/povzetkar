package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.OrchestrationManager;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
@Blocking
public class AdminResource {

    private static final Logger LOG = LoggerFactory.getLogger(AdminResource.class);

    private static final String ADMIN_REDIRECT_PATH = "/ui/admin";
    private static final String TAB_PARAM = "tab";
    private static final String HISTORY_TAB = "history";

    private final ViewUtils viewUtils;
    private final Template admin;
    private final ContentSourceDetailService contentSourceDetailService;
    private final OrchestrationManager orchestrationManager;

    @ConfigProperty(name = "povzetkar.ui.datetime.format")
    String dateTimeFormat;
    @ConfigProperty(name = "povzetkar.ui.date.format")
    String dateFormat;
    @ConfigProperty(name = "povzetkar.ui.admin.page-size")
    int pageSize;

    @Inject
    public AdminResource(@Location("admin.html") Template admin,
                         ContentSourceDetailService contentSourceDetailService,
                         OrchestrationManager orchestrationManager,
                         ViewUtils viewUtils) {
        this.admin = admin;
        this.contentSourceDetailService = contentSourceDetailService;
        this.orchestrationManager = orchestrationManager;
        this.viewUtils = viewUtils;
    }


    private void redirectBack(RoutingContext context, boolean success, String message) {
        if (success) {
            viewUtils.setSuccessMessage(context, message);
        } else {
            viewUtils.setErrorMessage(context, message);
        }
        viewUtils.handleRedirect(context, ADMIN_REDIRECT_PATH);
    }

    @Route(path = ADMIN_REDIRECT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getAdminPage(RoutingContext context) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("dateTimeFormat", dateTimeFormat);
        data.put("dateFormat", dateFormat);
        data.put("breadcrumbs", List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb("Admin", ADMIN_REDIRECT_PATH)
        ));

        String activeTab = context.request().getParam(TAB_PARAM);
        if (HISTORY_TAB.equals(activeTab)) {
            data.put("activeTab", HISTORY_TAB);
        } else {
            data.put("activeTab", "actions"); // Default to actions tab
        }

        List<ContentItem> pendingContentItems = ContentItem.find(
                "processingStatus = ?1 ORDER BY createdAt DESC", ProcessingStatus.PENDING
        ).page(0, pageSize).list();
        data.put("pendingContentItems", pendingContentItems);

        List<Summary> recentSummaries = Summary.find(
                "SELECT s FROM Summary s JOIN FETCH s.contentItem ORDER BY s.createdAt DESC"
        ).page(0, pageSize).list();
        data.put("recentSummaries", recentSummaries);

        List<Briefing> recentBriefings = Briefing.find("FROM Briefing b JOIN FETCH b.topic ORDER BY b.createdAt DESC").page(0, pageSize).list();
        data.put("recentBriefings", recentBriefings);

        List<ContentItem> failedContentItems = ContentItem.find(
                "processingStatus = ?1 OR processingStatus = ?2 ORDER BY createdAt DESC",
                ProcessingStatus.FAILED, ProcessingStatus.RETRY_LATER
        ).page(0, pageSize).list();
        data.put("failedContentItems", failedContentItems);

        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(admin.instance().data(data).render());
    }

    @Route(path = "/ui/admin/enrich-sources", methods = Route.HttpMethod.POST)
    @Blocking
    public void enrichSources(RoutingContext context) {
        try {
            contentSourceDetailService.enrichContentSources();
            redirectBack(context, true, "Content sources enrichment started successfully.");
        } catch (Exception e) {
            LOG.error("Failed to enrich content sources: {}", e.getMessage(), e);
            redirectBack(context, false, "Failed to start content sources enrichment: " + e.getMessage());
        }
    }

    @Route(path = "/ui/admin/run-orchestration", methods = Route.HttpMethod.POST)
    @Blocking
    public void runOrchestration(RoutingContext context) {
        Optional<String> startDateParamOptional = Optional.ofNullable(context.request().getParam("startDate"));
        Optional<String> endDateParamOptional = Optional.ofNullable(context.request().getParam("endDate"));

        LocalDate startDate = null;
        LocalDate endDate = null;
        String errorMessage = null;

        try {
            if (startDateParamOptional.isPresent() && !startDateParamOptional.get().isBlank()) {
                startDate = LocalDate.parse(startDateParamOptional.get());
            }
            if (endDateParamOptional.isPresent() && !endDateParamOptional.get().isBlank()) {
                endDate = LocalDate.parse(endDateParamOptional.get());
            }

            if (startDate != null && endDate != null && endDate.isBefore(startDate)) {
                errorMessage = "End date cannot be before start date.";
            } else if (startDate != null && startDate.isAfter(LocalDate.now())) {
                errorMessage = "Start date cannot be in the future.";
            } else if (endDate != null && endDate.isAfter(LocalDate.now())) {
                errorMessage = "End date cannot be in the future.";
            }

            if (errorMessage != null) {
                redirectBack(context, false, errorMessage);
                return;
            }

            orchestrationManager.runOrchestrationManually(startDate, endDate).await().indefinitely();
            redirectBack(context, true, "Orchestration process started successfully.");

        } catch (DateTimeParseException e) {
            LOG.error("Invalid date format provided for orchestration: {}", e.getMessage(), e);
            redirectBack(context, false, "Invalid date format. Please use YYYY-MM-DD.");
        } catch (Exception e) {
            LOG.error("Failed to start orchestration process: {}", e.getMessage(), e);
            redirectBack(context, false, "Failed to start orchestration process: " + e.getMessage());
        }
    }
}
