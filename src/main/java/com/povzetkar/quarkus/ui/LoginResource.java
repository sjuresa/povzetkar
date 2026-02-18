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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LoginResource {

    private static final Logger logger = LoggerFactory.getLogger(LoginResource.class);

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
            context.response().setStatusCode(302).putHeader("Location", TopicResource.TOPICS_PATH).end();
            return;
        }
        login.instance().createUni().subscribe()
                .with(html -> context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html));
    }

    @Route(path = "/logout", methods = HttpMethod.GET)
    public void logout(RoutingContext context) {
        String safeLogoutPath = "/";
        try {
            java.net.URI uri = new java.net.URI(logoutPath);
            // Ensure the URI is just a path, not an absolute URL with a scheme or host.
            if (uri.getScheme() == null && uri.getHost() == null) {
                safeLogoutPath = uri.getPath();
            }
        } catch (java.net.URISyntaxException _) {
            logger.warn("Configured logout path '{}' is not a valid URI. Using default safe path '/'", logoutPath);
        }
        logout.data("logoutPath", safeLogoutPath).createUni().subscribe()
                .with(html -> context.response().putHeader("Content-Type", "text/html").end(html));
    }

    @Route(path = "/", methods = HttpMethod.GET)
    public void index(RoutingContext context) {
        String location = securityIdentity.isAnonymous() ? "/login" : TopicResource.TOPICS_PATH;
        context.response().setStatusCode(302).putHeader("Location", location).end();
    }
}
