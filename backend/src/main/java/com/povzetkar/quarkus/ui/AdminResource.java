package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.service.ApiKeyUsageService;
import com.povzetkar.quarkus.service.ContentItemAdminService;
import com.povzetkar.quarkus.service.ContentItemService;
import com.povzetkar.quarkus.ui.util.Breadcrumb;
import com.povzetkar.quarkus.ui.util.DateTimeService;
import com.povzetkar.quarkus.ui.util.ViewUtils;
import com.povzetkar.quarkus.ui.util.WebUtils;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

@ApplicationScoped
public class AdminResource {

    private static final String ADMIN_REDIRECT_PATH = "/ui/admin";
    private static final String TAB_PARAM = "tab";
    private static final String HISTORY_TAB = "history";
    private static final String MESSAGE_KEY = "message";

    private final ViewUtils viewUtils;
    private final Template admin;
    private final ContentItemService contentItemService;
    private final DateTimeService dateTimeService;
    private final ApiKeyUsageService apiKeyUsageService;
    private final ContentItemAdminService contentItemAdminService;

    @ConfigProperty(name = "povzetkar.ui.admin.page-size")
    int pageSize;

    @Inject
    public AdminResource(@Location("admin.html") Template admin,
                         ViewUtils viewUtils,
                         ContentItemService contentItemService,
                         DateTimeService dateTimeService,
                         ApiKeyUsageService apiKeyUsageService,
                         ContentItemAdminService contentItemAdminService) {
        this.admin = admin;
        this.viewUtils = viewUtils;
        this.contentItemService = contentItemService;
        this.dateTimeService = dateTimeService;
        this.apiKeyUsageService = apiKeyUsageService;
        this.contentItemAdminService = contentItemAdminService;
    }

    @Route(path = ADMIN_REDIRECT_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    @Operation(hidden = true)
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

        // History lists are rendered only on the history tab — skip their queries on the actions tab
        if (HISTORY_TAB.equals(activeTab)) {
            addHistoryData(context, data);
        }

        // Other data
        data.put("genAiClientUsage", apiKeyUsageService.getUsageForToday());
        data.put("apiUsageTimezone", apiKeyUsageService.getTimezoneId());

        context.response()
                .putHeader("Content-Type", "text/html; charset=utf-8")
                .end(admin.instance().data(data).render());
    }

    private void addHistoryData(RoutingContext context, Map<String, Object> data) {
        int pendingPage = WebUtils.getIntegerQueryParam(context, "pendingPage", 1);
        int summariesPage = WebUtils.getIntegerQueryParam(context, "summariesPage", 1);
        int briefingsPage = WebUtils.getIntegerQueryParam(context, "briefingsPage", 1);
        int failedPage = WebUtils.getIntegerQueryParam(context, "failedPage", 1);

        // Pending Content Items
        var pendingIds = ContentItem.find("SELECT ci.id FROM ContentItem ci WHERE ci.processingStatus = ?1",
                newestFirst("ci"), ProcessingStatus.PENDING).project(Long.class);
        addPagedDataToTemplate(data, "pendingContentItems", "pendingTotalPages", "pendingCurrentPage", pendingPage,
                pendingIds, ids -> ContentItem.listByIdsWithAssociations(ids, newestFirst("c")));

        // Recent Summaries
        var summaryIds = Summary.find("SELECT s.id FROM Summary s", newestFirst("s")).project(Long.class);
        addPagedDataToTemplate(data, "recentSummaries", "summariesTotalPages", "summariesCurrentPage", summariesPage,
                summaryIds, ids -> Summary.listByIdsWithAssociations(ids, newestFirst("s")));

        // Recent Briefings
        var briefingIds = Briefing.find("SELECT b.id FROM Briefing b", newestFirst("b")).project(Long.class);
        addPagedDataToTemplate(data, "recentBriefings", "briefingsTotalPages", "briefingsCurrentPage", briefingsPage,
                briefingIds, ids -> Briefing.list("FROM Briefing b JOIN FETCH b.topic WHERE b.id IN ?1", newestFirst("b"), ids));

        // Failed Content Items
        var failedIds = ContentItem.find("SELECT ci.id FROM ContentItem ci WHERE ci.processingStatus IN (?1, ?2, ?3)",
                newestFirst("ci"), ProcessingStatus.FAILED, ProcessingStatus.RETRY_LATER, ProcessingStatus.IGNORED).project(Long.class);
        addPagedDataToTemplate(data, "failedContentItems", "failedTotalPages", "failedCurrentPage", failedPage,
                failedIds, ids -> ContentItem.listByIdsWithAssociations(ids, newestFirst("c")));
    }

    /**
     * Pages over ids, then loads that page's entities with their fetches. Paging a collection-fetch
     * query directly fails on Hibernate 7.4 when its root is the polymorphic {@link ContentItem}: the
     * derived table it generates omits subclass columns. The other lists use the same helper for consistency.
     */
    private <T> void addPagedDataToTemplate(Map<String, Object> data, String listKey, String totalPagesKey, String currentPageKey, int page,
                                            PanacheQuery<Long> idQuery, Function<List<Long>, List<T>> loader) {
        if (page < 1) {
            page = 1;
        }
        List<Long> ids = idQuery.page(page - 1, pageSize).list();
        data.put(listKey, ids.isEmpty() ? List.of() : loader.apply(ids));
        data.put(totalPagesKey, idQuery.pageCount());
        data.put(currentPageKey, page);
    }

    /**
     * Newest first, with the id as tiebreaker so rows sharing a createdAt neither repeat nor vanish
     * across pages. Qualified by the query's alias: unqualified names are ambiguous in fetch joins.
     */
    private static Sort newestFirst(String alias) {
        return Sort.by(alias + ".createdAt", Sort.Direction.Descending).and(alias + ".id", Sort.Direction.Descending);
    }

    @Route(path = "/api/v1/admin/content-items/:id/fetch-transcript", methods = Route.HttpMethod.POST)
    @Blocking
    @Operation(hidden = true)
    public void fetchTranscript(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, "id", "Invalid content item ID format.",
                msg -> context.response().setStatusCode(400).end(new JsonObject().put(MESSAGE_KEY, msg).encode()));
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
                .end(new JsonObject().put(MESSAGE_KEY, message).encode());
    }

    @Route(path = "/api/v1/admin/content-items/:id/status", methods = Route.HttpMethod.POST)
    @Blocking
    @Operation(hidden = true)
    public void updateContentItemStatus(RoutingContext context) {
        Optional<Long> idOptional = WebUtils.getLongPathParam(context, "id", "Invalid content item ID format.",
                msg -> context.response().setStatusCode(400).end(new JsonObject().put(MESSAGE_KEY, msg).encode()));
        if (idOptional.isEmpty()) {
            return;
        }
        long contentItemId = idOptional.get();

        String statusParam = context.request().getFormAttribute("status");
        ProcessingStatus newStatus;
        try {
            newStatus = ProcessingStatus.valueOf(statusParam);
        } catch (IllegalArgumentException | NullPointerException _) {
            context.response().setStatusCode(400).end(new JsonObject().put(MESSAGE_KEY, "Invalid status value provided.").encode());
            return;
        }

        Optional<ContentItem> updatedItem = contentItemAdminService.updateStatus(contentItemId, newStatus);
        if (updatedItem.isPresent()) {
            context.response().setStatusCode(200).end(new JsonObject().put(MESSAGE_KEY, "Content item status updated successfully.").encode());
        } else {
            context.response().setStatusCode(404).end(new JsonObject().put(MESSAGE_KEY, "Failed to update content item status. Content item not found.").encode());
        }
    }
}
