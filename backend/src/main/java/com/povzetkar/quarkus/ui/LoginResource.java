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

    @ConfigProperty(name = "povzetkar.auth.post-login-uri", defaultValue = TopicResource.TOPICS_PATH)
    String postLoginUri;

    @ConfigProperty(name = "povzetkar.auth.post-logout-uri", defaultValue = LOGIN_PATH)
    String postLogoutUri;

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
            redirect(context, postLoginUri);
            return;
        }
        login.instance().createUni().subscribe()
                .with(html -> context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html),
                        context::fail);
    }

    @Route(path = "/auth/login", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void authLogin(RoutingContext context) {
        // This path is protected by OIDC in application.properties.
        // Once here, the user is authenticated.
        String dynamicRedirect = context.request().getParam("redirect");
        String destination = postLoginUri;
        // Prevent open redirect vulnerability. The path must be local and not protocol-relative (//).
        if (dynamicRedirect != null && !dynamicRedirect.isBlank() && dynamicRedirect.startsWith("/") && !dynamicRedirect.startsWith("//") && !dynamicRedirect.contains("..")) {
            destination = dynamicRedirect;
        }
                
        redirect(context, destination);
    }

    @Route(path = "/logout", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void logout(RoutingContext context) {
        if (securityIdentity.isAnonymous()) {
            redirect(context, postLogoutUri);
            return;
        }

        oidcSession.logout().subscribe().with(
                _ -> {
                    logger.info("Successfully destroyed local application session.");
                    redirect(context, postLogoutUri);
                },
                err -> {
                    logger.error("Failed to perform local OIDC logout", err);
                    redirect(context, postLogoutUri);
                });
    }

    @Route(path = "/", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void index(RoutingContext context) {
        String location = securityIdentity.isAnonymous() ? postLogoutUri : postLoginUri;
        redirect(context, location);
    }

    private void redirect(RoutingContext context, String location) {
        context.response().setStatusCode(302).putHeader(LOCATION_HEADER, location).end();
    }
}
