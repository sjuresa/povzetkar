package com.povzetkar.quarkus.ui;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.jetbrains.annotations.Blocking;

@ApplicationScoped
@Blocking
public class AdminResource {

    private final Template admin;

    public AdminResource(@Location("admin.html") Template admin) {
        this.admin = admin;
    }

    @Route(path = "/ui/admin", methods = Route.HttpMethod.GET)
    public void getAdminPage(RoutingContext context) {
        context.response().end(admin.instance().render());
    }
}
