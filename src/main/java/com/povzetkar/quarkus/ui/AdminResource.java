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
import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
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

    private static final String SUCCESS_MESSAGE_KEY = "successMessage";
    private static final String ERROR_MESSAGE_KEY = "errorMessage";
    private static final String LOCATION_HEADER = "Location";
    private static final String ADMIN_REDIRECT_PATH = "/ui/admin";

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
                         OrchestrationManager orchestrationManager) {
        this.admin = admin;
        this.contentSourceDetailService = contentSourceDetailService;
        this.orchestrationManager = orchestrationManager;
    }

    public void init(@Observes Router router, Vertx vertx) {
        // 1️⃣ Enables reading form data, JSON bodies, etc.
        router.route().order(-1).handler(BodyHandler.create());

        // 2️⃣ Enables sessions globally
        router.route().order(0).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    }

    private void redirectBack(RoutingContext context, String key, String message) {
        context.session().put(key, message);
        context.response()
                .putHeader(LOCATION_HEADER, ADMIN_REDIRECT_PATH)
                .setStatusCode(303)
                .end();
    }

    @Route(path = ADMIN_REDIRECT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getAdminPage(RoutingContext context) {
        String successMessage = context.session().get(SUCCESS_MESSAGE_KEY);
        String errorMessage = context.session().get(ERROR_MESSAGE_KEY);
        context.session().remove(SUCCESS_MESSAGE_KEY);
        context.session().remove(ERROR_MESSAGE_KEY);

        Map<String, Object> data = new HashMap<>();
        data.put(SUCCESS_MESSAGE_KEY, successMessage);
        data.put(ERROR_MESSAGE_KEY, errorMessage);
        data.put("dateTimeFormat", dateTimeFormat);
        data.put("dateFormat", dateFormat);

        // Fetch pending content items
        List<ContentItem> pendingContentItems = ContentItem.find(
                "processingStatus = ?1 ORDER BY createdAt DESC", ProcessingStatus.PENDING
        ).page(0, pageSize).list();
        data.put("pendingContentItems", pendingContentItems);

        // Fetch recent summaries with their content items
        List<Summary> recentSummaries = Summary.find(
                "SELECT s FROM Summary s JOIN FETCH s.contentItem ORDER BY s.createdAt DESC"
        ).page(0, pageSize).list();
        data.put("recentSummaries", recentSummaries);

        // Fetch recent briefings
        List<Briefing> recentBriefings = Briefing.findAll(Sort.by("createdAt", Sort.Direction.Descending)).page(0, pageSize).list();
        data.put("recentBriefings", recentBriefings);

        // Fetch failed content items
        List<ContentItem> failedContentItems = ContentItem.find(
                "processingStatus = ?1 OR processingStatus = ?2 ORDER BY createdAt DESC",
                ProcessingStatus.FAILED, ProcessingStatus.RETRY_LATER
        ).page(0, pageSize).list();
        data.put("failedContentItems", failedContentItems);


        context.response().end(admin.instance().data(data).render());
    }

    @Route(path = "/ui/admin/enrich-sources", methods = Route.HttpMethod.POST)
    @Blocking
    public void enrichSources(RoutingContext context) {
        try {
            contentSourceDetailService.enrichContentSources();
            redirectBack(context, SUCCESS_MESSAGE_KEY, "Content sources enrichment started successfully.");
        } catch (Exception e) {
            redirectBack(context, ERROR_MESSAGE_KEY, "Failed to start content sources enrichment: " + e.getMessage());
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
                redirectBack(context, ERROR_MESSAGE_KEY, errorMessage);
                return;
            }

            orchestrationManager.runOrchestrationManually(startDate, endDate)
                    .subscribe().with(
                            success -> redirectBack(context, SUCCESS_MESSAGE_KEY, "Orchestration process started successfully."),
                            failure -> redirectBack(context, ERROR_MESSAGE_KEY, "Failed to start orchestration process: " + failure.getMessage())
                    );

        } catch (DateTimeParseException e) {
            redirectBack(context, ERROR_MESSAGE_KEY, "Invalid date format. Please use YYYY-MM-DD.");
        } catch (Exception e) {
            redirectBack(context, ERROR_MESSAGE_KEY, "Failed to start orchestration process: " + e.getMessage());
        }
    }
}
