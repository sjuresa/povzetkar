package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.service.ApiKeyUsageService;
import com.povzetkar.quarkus.service.ContentItemService;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.DateTimeService;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class AdminResource {

    private static final String ADMIN_REDIRECT_PATH = "/ui/admin";
    private static final String TAB_PARAM = "tab";
    private static final String HISTORY_TAB = "history";

    private final ViewUtils viewUtils;
    private final Template admin;
    private final ContentItemService contentItemService;
    private final DateTimeService dateTimeService;
    private final ApiKeyUsageService apiKeyUsageService;

    @ConfigProperty(name = "povzetkar.ui.admin.page-size")
    int pageSize;

    @Inject
    public AdminResource(@Location("admin.html") Template admin,
                         ViewUtils viewUtils,
                         ContentItemService contentItemService,
                         DateTimeService dateTimeService,
                         ApiKeyUsageService apiKeyUsageService) {
        this.admin = admin;
        this.viewUtils = viewUtils;
        this.contentItemService = contentItemService;
        this.dateTimeService = dateTimeService;
        this.apiKeyUsageService = apiKeyUsageService;
    }

    @Route(path = ADMIN_REDIRECT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getAdminPage(RoutingContext context) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("dateTimeService", dateTimeService);
        data.put("breadcrumbs", List.of(
                ViewUtils.HOME_BREADCRUMB,
                new Breadcrumb("Admin", ADMIN_REDIRECT_PATH)
        ));
        data.put("ADMIN_REDIRECT_PATH", ADMIN_REDIRECT_PATH);

        String activeTab = context.request().getParam(TAB_PARAM);
        data.put("activeTab", HISTORY_TAB.equals(activeTab) ? HISTORY_TAB : "actions");

        // Pagination
        int pendingPage = WebUtils.getIntegerQueryParam(context, "pendingPage", 1);
        int summariesPage = WebUtils.getIntegerQueryParam(context, "summariesPage", 1);
        int briefingsPage = WebUtils.getIntegerQueryParam(context, "briefingsPage", 1);
        int failedPage = WebUtils.getIntegerQueryParam(context, "failedPage", 1);

        // Pending Content Items
        var pendingQuery = ContentItem.find(
                "SELECT ci FROM ContentItem ci LEFT JOIN FETCH ci.contentSource cs LEFT JOIN FETCH cs.topics WHERE ci.processingStatus = ?1 ORDER BY ci.createdAt DESC",
                ProcessingStatus.PENDING
        );
        addPagedDataToTemplate(data, "pendingContentItems", "pendingTotalPages", "pendingCurrentPage", pendingPage, pendingQuery);

        // Recent Summaries
        var summaryQuery = Summary.find(
                "SELECT s FROM Summary s JOIN FETCH s.contentItem ci LEFT JOIN FETCH ci.contentSource cs LEFT JOIN FETCH cs.topics ORDER BY s.createdAt DESC"
        );
        addPagedDataToTemplate(data, "recentSummaries", "summariesTotalPages", "summariesCurrentPage", summariesPage, summaryQuery);

        // Recent Briefings
        var briefingQuery = Briefing.find("FROM Briefing b JOIN FETCH b.topic ORDER BY b.createdAt DESC");
        addPagedDataToTemplate(data, "recentBriefings", "briefingsTotalPages", "briefingsCurrentPage", briefingsPage, briefingQuery);

        // Failed Content Items
        var failedQuery = ContentItem.find(
                "SELECT ci FROM ContentItem ci LEFT JOIN FETCH ci.contentSource cs LEFT JOIN FETCH cs.topics WHERE ci.processingStatus = ?1 OR ci.processingStatus = ?2 ORDER BY ci.createdAt DESC",
                ProcessingStatus.FAILED, ProcessingStatus.RETRY_LATER
        );
        addPagedDataToTemplate(data, "failedContentItems", "failedTotalPages", "failedCurrentPage", failedPage, failedQuery);

        // Other data
        data.put("genAiClientUsage", apiKeyUsageService.getUsageForToday());
        data.put("apiUsageTimezone", apiKeyUsageService.getTimezoneId());

        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(admin.instance().data(data).render());
    }

    private void addPagedDataToTemplate(Map<String, Object> data, String listKey, String totalPagesKey, String currentPageKey, int page, PanacheQuery<?> query) {
        if (page < 1) {
            page = 1;
        }
        data.put(listKey, query.page(page, pageSize).list());
        data.put(totalPagesKey, query.pageCount());
        data.put(currentPageKey, page);
    }

    @Route(path = "/api/v1/admin/content-items/:id/fetch-transcript", methods = Route.HttpMethod.POST)
    @Blocking
    public void fetchTranscript(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, "id", "Invalid content item ID format.",
                msg -> context.response().setStatusCode(400).end(new JsonObject().put("message", msg).encode()));
        if (idOptional.isEmpty()) {
            return;
        }
        long contentItemId = idOptional.get();

        ContentItemService.FetchTranscriptResult result = contentItemService.fetchTranscript(contentItemId);

        final int statusCode;
        final String message;

        switch (result) {
            case SUCCESS -> {
                statusCode = 200;
                message = "Transcript fetched successfully.";
            }
            case NOT_FOUND -> {
                statusCode = 404;
                message = "Content item not found.";
            }
            case NOT_A_VIDEO -> {
                statusCode = 400;
                message = "Content item is not a YouTube video.";
            }
            case TRANSCRIPT_UNAVAILABLE -> {
                statusCode = 422;
                message = "Transcript is unavailable for this video.";
            }
            default -> {
                statusCode = 500;
                message = "Failed to fetch transcript due to an internal server error.";
            }
        }

        context.response()
                .setStatusCode(statusCode)
                .end(new JsonObject().put("message", message).encode());
    }
}
