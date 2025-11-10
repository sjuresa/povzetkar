package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.ui.util.ViewUtils;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.smallrye.common.annotation.Blocking;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

import static com.povzetkar.quarkus.ui.TopicResource.UI_BASE_PATH;

@ApplicationScoped
public class HomeResource {

    private final ViewUtils viewUtils;

    private final Template home;

    public HomeResource(@Location("home.html") Template home, ViewUtils viewUtils) {
        this.home = home;
        this.viewUtils = viewUtils;
    }

    @Route(path = UI_BASE_PATH, methods = Route.HttpMethod.GET)
    @Blocking
    public void getHomePage(RoutingContext context) {
        Map<String, Object> data = viewUtils.createTemplateData(context);
        data.put("breadcrumbs", List.of(ViewUtils.HOME_BREADCRUMB));
        context.response().end(home.instance().data(data).render());
    }
}
