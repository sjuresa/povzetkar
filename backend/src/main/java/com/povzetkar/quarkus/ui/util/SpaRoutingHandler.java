package com.povzetkar.quarkus.ui.util;

import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Handles SPA routing by rerouting frontend routes to index.html, so client-side routing (e.g., /dashboard)
 * works on page refresh and for deep links. Any other path that reaches this fallback answers a bare 404
 * (POV-221), so probe traffic is not served the SPA shell with 200.
 */
@ApplicationScoped
public class SpaRoutingHandler {

    private static final Logger logger = LoggerFactory.getLogger(SpaRoutingHandler.class);

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api", "/ui", "/q", "/static", "/assets", "/login", "/logout", "/auth", "/favicon.ico"
    );

    /**
     * The URL patterns of {@code frontend/src/router.tsx}, one {@code *} per path parameter ({@code $sourceId}); a
     * route added, moved or removed there must be mirrored here, which {@code SpaRouteAllowListTest} enforces.
     */
    static final Set<String> SPA_ROUTES = Set.of(
            "/dashboard", "/me", "/topics", "/briefings", "/briefings/*", "/sources", "/sources/*",
            "/admin", "/admin/audit-log", "/admin/content", "/admin/jobs", "/admin/users", "/s/*"
    );

    private static final List<String[]> SPA_ROUTE_SEGMENTS = SPA_ROUTES.stream()
            .map(route -> route.substring(1).split("/"))
            .toList();

    /**
     * Whether a path is a frontend route: it matches one of {@link #SPA_ROUTES} segment by segment
     * ({@code /admin/jobs} yes, {@code /admin/does-not-exist} and {@code /sources/7/extra} no), case-insensitively
     * like the TanStack router, with at most one trailing slash; it has no dot-segment ({@code /sources/.git}) and
     * is not a file ({@code /admin/config.php}).
     */
    public static boolean isSpaRoute(String path) {
        if (path == null || !path.startsWith("/") || path.contains("/.") || isFile(path)) {
            return false;
        }
        String withoutTrailingSlash = path.length() > 1 && path.endsWith("/")
                ? path.substring(0, path.length() - 1) : path;
        String[] segments = withoutTrailingSlash.substring(1).toLowerCase(Locale.ROOT).split("/", -1);
        return SPA_ROUTE_SEGMENTS.stream().anyMatch(route -> matches(route, segments));
    }

    /** Literal segments must be equal, {@code *} accepts any non-empty segment. */
    private static boolean matches(String[] route, String[] segments) {
        if (route.length != segments.length) {
            return false;
        }
        for (int i = 0; i < route.length; i++) {
            if (segments[i].isEmpty() || !("*".equals(route[i]) || route[i].equals(segments[i]))) {
                return false;
            }
        }
        return true;
    }

    /** An extension in the last path segment. */
    public static boolean isFile(String path) {
        return path.lastIndexOf('.') > path.lastIndexOf('/');
    }

    /** The first segment with its leading slash: {@code /sources/7} → {@code /sources}. */
    private static String firstSegment(String path) {
        int segmentEnd = path.indexOf('/', 1);
        return segmentEnd < 0 ? path : path.substring(0, segmentEnd);
    }

    /**
     * Catch-all route for SPA navigation paths.
     * Order is high to let specific routes (API, Static, Legacy UI) match first.
     */
    @Route(path = "/*", order = 10000)
    public void handle(RoutingContext rc) {
        String path = rc.normalizedPath();

        // If it's the root path, we handle it elsewhere or let it be
        if (path.equals("/") || path.equals("/index.html")) {
            rc.next();
            return;
        }

        if (isSpaRoute(path)) {
            if (logger.isDebugEnabled()) {
                logger.debug("[SpaRouting] Rerouting path '{}' to /index.html", path);
            }
            rc.reroute("/index.html");
            return;
        }

        // Files (.html included) and excluded prefixes (API, internal, etc., matched as a whole first segment)
        // go on to their own handlers, which serve them or answer their own 404s
        if (isFile(path) || EXCLUDED_PREFIXES.contains(firstSegment(path))) {
            rc.next();
            return;
        }

        rc.response().setStatusCode(404).end();
    }
}
