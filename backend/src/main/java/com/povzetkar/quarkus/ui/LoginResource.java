package com.povzetkar.quarkus.ui;

import com.povzetkar.quarkus.util.OriginValidator;
import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
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
    OriginValidator originValidator;

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
            String normalizedPath = new URI(dynamicRedirect).normalize().getPath();
            if (normalizedPath != null && !normalizedPath.startsWith("/..")) {
                return normalizedPath;
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
        String base = determineBaseUrl(context);
        String finalLocation = applyBaseToLocation(base, location);

        logger.info("Redirecting to: {}", finalLocation);
        context.response()
                .setStatusCode(302)
                .putHeader(LOCATION_HEADER, finalLocation)
                .end();
    }

    private String determineBaseUrl(RoutingContext context) {
        String requesterBase = context.request().getHeader("Origin");
        if (requesterBase == null) {
            requesterBase = context.request().getHeader("Referer");
        }

        if (requesterBase == null) {
            return null;
        }

        if (originValidator.isAllowed(requesterBase)) {
            try {
                URI originUri = new URI(requesterBase);
                return originUri.getScheme() + "://" + originUri.getAuthority();
            } catch (URISyntaxException e) {
                logger.warn("Failed to parse requester base origin: {}", requesterBase, e);
                return null;
            }
        }
        return null;
    }

    private String applyBaseToLocation(String base, String location) {
        if (base == null) {
            return location;
        }

        // Prepend base to all relative paths. Paths are sanitized against open redirects
        // before this method is called.
        if (location.startsWith("/")) {
            return base + location;
        }

        // The location is likely an absolute URL (e.g., for backward compatibility).
        // The multi-host feature relies on relative paths, so we return it as-is.
        return location;
    }
}
