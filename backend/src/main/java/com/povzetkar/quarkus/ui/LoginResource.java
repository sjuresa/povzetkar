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

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

@ApplicationScoped
public class LoginResource {

    private static final Logger logger = LoggerFactory.getLogger(LoginResource.class);
    private static final String LOCATION_HEADER = "Location";
    private static final String LOGIN_PATH = "/login";
    public static final String REDIRECT_PARAM = "redirect";

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
        String dynamicRedirect = context.request().getParam(REDIRECT_PARAM);
        if (dynamicRedirect == null) {
            Object redirectObj = context.get(REDIRECT_PARAM);
            if (redirectObj instanceof String redirect) {
                dynamicRedirect = redirect;
            }
        }

        if (!securityIdentity.isAnonymous()) {
            redirect(context, resolveSafeDestination(dynamicRedirect));
            return;
        }
        login.instance().data(REDIRECT_PARAM, dynamicRedirect).createUni().subscribe()
                .with(html -> context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(html),
                        context::fail);
    }

    @Route(path = "/auth/login", methods = HttpMethod.GET)
    @Operation(hidden = true)
    public void authLogin(RoutingContext context) {
        String dynamicRedirect = context.request().getParam(REDIRECT_PARAM);
        redirect(context, resolveSafeDestination(dynamicRedirect));
    }

    private String resolveSafeDestination(String dynamicRedirect) {
        if (dynamicRedirect == null || dynamicRedirect.isBlank() || !dynamicRedirect.startsWith("/")) {
            return postLoginUri;
        }

        try {
            // Decode the path to prevent bypasses using URL-encoded characters.
            String decodedPath = URLDecoder.decode(dynamicRedirect, StandardCharsets.UTF_8);

            // After decoding, a protocol-relative URL attack would start with "//".
            if (decodedPath.startsWith("//")) {
                logger.warn("Ignoring redirect parameter attempting protocol-relative redirect: {}", dynamicRedirect);
                return postLoginUri;
            }

            // It's safe to normalize the original path now, as the main vector is checked.
            URI normalizedUri = new URI(dynamicRedirect).normalize();
            // Validate on the decoded path — normalize() does not decode percent-encoded segments,
            // so %2e%2e would bypass a raw-path check for "/.." traversal.
            String decoded = Optional.ofNullable(normalizedUri.getPath()).orElse("");
            // getAuthority() == null guards against any URI that resolved an authority component
            // (e.g. a double-slash that slipped past the decoded check above).
            // Also block "/\" — some browsers normalise backslash to "/" which could bypass the "//" check.
            if (!decoded.startsWith("/..") && !decoded.startsWith("/\\") && normalizedUri.getAuthority() == null) {
                return normalizedUri.toString();
            }
        } catch (IllegalArgumentException | URISyntaxException e) {
            logger.warn("Ignoring malformed redirect parameter: {}", dynamicRedirect, e);
        }
        return postLoginUri;
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
                    // OidcSession.logout() expires the q_session cookie(s) itself.
                    logger.info("Successfully destroyed local application session.");
                    redirect(context, postLogoutUri);
                },
                err -> {
                    logger.error("Failed to perform local OIDC logout", err);
                    redirect(context, postLogoutUri);
                });
    }

    private void redirect(RoutingContext context, String location) {
        // Relative: the browser resolves it against the origin it is on (public host, or the Vite dev server)
        logger.info("Redirecting to: {}", location);
        context.response()
                .setStatusCode(302)
                .putHeader(LOCATION_HEADER, location)
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .putHeader("Pragma", "no-cache")
                .putHeader("Expires", "0")
                .end();
    }
}
