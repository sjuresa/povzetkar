package com.povzetkar.quarkus.ui;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LoginResource {

    private static final Logger logger = LoggerFactory.getLogger(LoginResource.class);
    private static final String LOCATION_HEADER = "Location";
    private static final String LOGIN_PATH = "/login";

    private final Template login;
    private final SecurityIdentity securityIdentity;
    private final io.quarkus.oidc.OidcSession oidcSession;

    @Inject
    public LoginResource(@Location("login.html") Template login,
            SecurityIdentity securityIdentity,
            io.quarkus.oidc.OidcSession oidcSession) {
        this.login = login;
        this.securityIdentity = securityIdentity;
        this.oidcSession = oidcSession;
    }

    @Route(path = "/login", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void login(RoutingContext context) {
        if (!securityIdentity.isAnonymous()) {
            redirect(context, TopicResource.TOPICS_PATH);
            return;
        }
        login.instance().createUni().subscribe()
                .with(html -> context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html),
                        context::fail);
    }

    @Route(path = "/logout", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void logout(RoutingContext context) {
        if (securityIdentity.isAnonymous()) {
            redirect(context, LOGIN_PATH);
            return;
        }

        oidcSession.logout().subscribe().with(
                _ -> {
                    logger.info("Successfully destroyed local application session.");
                    redirect(context, LOGIN_PATH);
                },
                err -> {
                    logger.error("Failed to perform local OIDC logout", err);
                    redirect(context, LOGIN_PATH);
                });
    }

    @Route(path = "/", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void index(RoutingContext context) {
        String location = securityIdentity.isAnonymous() ? LOGIN_PATH : TopicResource.TOPICS_PATH;
        redirect(context, location);
    }

    private void redirect(RoutingContext context, String location) {
        context.response().setStatusCode(302).putHeader(LOCATION_HEADER, location).end();
    }
}
