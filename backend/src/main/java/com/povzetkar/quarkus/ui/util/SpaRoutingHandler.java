package com.povzetkar.quarkus.ui.util;

import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
     * First path segments of the top-level routes in {@code frontend/src/router.tsx}; a new route there must be
     * added here, which {@code SpaRouteAllowListTest} enforces.
     */
    static final Set<String> SPA_ROUTE_PREFIXES = Set.of(
            "/dashboard", "/me", "/topics", "/briefings", "/sources", "/admin", "/s"
    );

    /**
     * Whether a path belongs to a frontend route: its first segment is on the allow-list, matched as a whole
     * segment ({@code /s/x} yes, {@code /secret} no) and case-insensitively like the TanStack router; it has no
     * dot-segment ({@code /sources/.git}) and is not a file ({@code /admin/config.php}).
     */
    public static boolean isSpaRoute(String path) {
        if (path == null || !path.startsWith("/") || path.contains("/.") || isFile(path)) {
            return false;
        }
        return SPA_ROUTE_PREFIXES.contains(firstSegment(path).toLowerCase(Locale.ROOT));
    }

    /** An extension in the last path segment. */
    private static boolean isFile(String path) {
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
