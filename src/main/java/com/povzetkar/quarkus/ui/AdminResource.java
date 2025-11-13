package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
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
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class AdminResource {

    static {
        LoggerFactory.getLogger(AdminResource.class);
    }

    private static final String ADMIN_REDIRECT_PATH = "/ui/admin";
    private static final String TAB_PARAM = "tab";
    private static final String HISTORY_TAB = "history";

    private final ViewUtils viewUtils;
    private final Template admin;

    @ConfigProperty(name = "povzetkar.ui.datetime.format")
    String dateTimeFormat;
    @ConfigProperty(name = "povzetkar.ui.date.format")
    String dateFormat;
    @ConfigProperty(name = "povzetkar.ui.admin.page-size")
    int pageSize;

    @Inject
    public AdminResource(@Location("admin.html") Template admin,
                         ViewUtils viewUtils) {
        this.admin = admin;
        this.viewUtils = viewUtils;
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
}
