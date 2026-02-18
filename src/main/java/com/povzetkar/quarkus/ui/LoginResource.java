package com.povzetkar.quarkus.ui;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class LoginResource {

    private final Template login;
    private final Template logout;
    private final SecurityIdentity securityIdentity;
    private final String logoutPath;

    @Inject
    public LoginResource(@Location("login.html") Template login,
            @Location("logout.html") Template logout,
            SecurityIdentity securityIdentity,
            @ConfigProperty(name = "quarkus.oidc.logout.path", defaultValue = "/logout") String logoutPath) {
        this.login = login;
        this.logout = logout;
        this.securityIdentity = securityIdentity;
        this.logoutPath = logoutPath;
    }

    @Route(path = "/login", methods = HttpMethod.GET)
    public void login(RoutingContext context) {
        if (!securityIdentity.isAnonymous()) {
            context.response().setStatusCode(302).putHeader("Location", "/ui/topics").end();
            return;
        }
        context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(login.render());
    }

    @Route(path = "/logout", methods = HttpMethod.GET)
    public void logout(RoutingContext context) {
        context.response().putHeader("Content-Type", "text/html").end(logout.data("logoutPath", logoutPath).render());
    }

    @Route(path = "/", methods = HttpMethod.GET)
    public void index(RoutingContext context) {
        context.response().setStatusCode(302).putHeader("Location", "/login").end();
    }
}
