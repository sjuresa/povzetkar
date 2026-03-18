package com.povzetkar.quarkus.ui.util;

import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Set;

/**
 * Handles SPA routing by rerouting non-API, non-static paths to index.html.
 * This ensures that client-side routing (e.g., /dashboard) works on page refresh.
 */
@ApplicationScoped
public class SpaRoutingHandler {

    private static final Set<String> EXCLUDED_PREFIXES = Set.of(
            "/api", "/ui", "/q", "/static", "/assets", "/login", "/logout", "/auth", "/favicon.ico"
    );

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

        // Check for a file extension in the last path segment (exclude .html)
        if (path.lastIndexOf('.') > path.lastIndexOf('/') && !path.endsWith(".html")) {
            rc.next();
            return;
        }

        // If it's not an excluded path (API, internal, etc.), reroute to index.html
        if (EXCLUDED_PREFIXES.stream().noneMatch(path::startsWith)) {
            rc.reroute("/index.html");
        } else {
            rc.next();
        }
    }
}
