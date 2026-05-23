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
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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

    @ConfigProperty(name = "quarkus.http.auth.session.cookie-name")
    String sessionCookieName;

    private final OriginValidator originValidator;

    @Inject
    public LoginResource(@Location("login.html") Template login,
                         SecurityIdentity securityIdentity,
                         io.quarkus.oidc.OidcSession oidcSession,
                         OriginValidator originValidator) {
        this.login = login;
        this.securityIdentity = securityIdentity;
        this.oidcSession = oidcSession;
        this.originValidator = originValidator;
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
        String destination = resolveSafeDestination(dynamicRedirect);

        // If no explicit redirect and we are accessing backend directly (no Origin/Referer)
        if (dynamicRedirect == null && determineBaseUrl(context) == null) {
            logger.info("[Auth] No explicit redirect or Origin/Referer detected, defaulting to postLoginUri: {}", postLoginUri);
            destination = postLoginUri;
        }

        redirect(context, destination);
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
            String normalizedPath = normalizedUri.getPath();
            if (normalizedPath != null && !normalizedPath.startsWith("/..")) {
                String query = normalizedUri.getQuery();
                return query != null ? normalizedPath + "?" + query : normalizedPath;
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
                    // Manually clear the session cookie as a safety measure to ensure browser reflects logout immediately
                    context.response().addCookie(io.vertx.core.http.Cookie.cookie(sessionCookieName, "")
                            .setMaxAge(0)
                            .setPath("/"));
                    redirect(context, postLogoutUri);
                },
                err -> {
                    logger.error("Failed to perform local OIDC logout", err);
                    redirect(context, postLogoutUri);
                });
    }

    private void redirect(RoutingContext context, String location) {
        String base = determineBaseUrl(context);
        String finalLocation = applyBaseToLocation(base, location);

        logger.info("Redirecting to: {}", finalLocation);
        context.response()
                .setStatusCode(302)
                .putHeader(LOCATION_HEADER, finalLocation)
                .putHeader("Cache-Control", "no-cache, no-store, must-revalidate")
                .putHeader("Pragma", "no-cache")
                .putHeader("Expires", "0")
                .end();
    }

    private String determineBaseUrl(RoutingContext context) {
        String origin = context.request().getHeader("Origin");

        if (origin == null) {
            String referer = context.request().getHeader("Referer");
            if (referer != null) {
                try {
                    URI refererUri = new URI(referer);
                    String scheme = refererUri.getScheme();
                    String authority = refererUri.getAuthority();
                    if (scheme != null && authority != null) {
                        origin = scheme + "://" + authority;
                    }
                } catch (URISyntaxException e) {
                    logger.warn("Failed to parse Referer header to extract origin: {}", referer, e);
                }
            }
        }

        if (origin != null && originValidator.isAllowed(origin)) {
            return origin;
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
