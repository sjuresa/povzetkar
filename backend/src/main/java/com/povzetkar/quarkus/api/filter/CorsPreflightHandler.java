package com.povzetkar.quarkus.api.filter;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;

/**
 * Low-level Vert.x handler to catch OPTIONS requests before OIDC interrupts them.
 * This is the most reliable way to handle CORS preflight in Quarkus OIDC web-app mode.
 */
@ApplicationScoped
public class CorsPreflightHandler {

    @Inject
    Router router;

    void onStart(@Observes StartupEvent ev) {
        router.route().order(-10000).handler(rc -> {
            String origin = rc.request().getHeader("Origin");
            boolean isFromFrontend = "http://localhost:5173".equals(origin);

            if (isFromFrontend) {
                // Ensure CORS headers are ALWAYS present on any response to the frontend
                rc.addHeadersEndHandler(v -> {
                    if (!rc.response().headers().contains("Access-Control-Allow-Origin")) {
                        rc.response().putHeader("Access-Control-Allow-Origin", origin);
                        rc.response().putHeader("Access-Control-Allow-Credentials", "true");
                    }
                });
            }

            if (rc.request().method() == HttpMethod.OPTIONS) {
                rc.response()
                    .putHeader("Access-Control-Allow-Origin", isFromFrontend ? origin : "*")
                    .putHeader("Access-Control-Allow-Methods", "GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH")
                    .putHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Requested-With, X-Correlation-ID, Idempotency-Key, If-Match, Accept")
                    .putHeader("Access-Control-Allow-Credentials", "true")
                    .putHeader("Access-Control-Max-Age", "3600")
                    .setStatusCode(200)
                    .end();
            } else {
                // For AJAX requests, convert OIDC redirects (302) to 401 Unauthorized
                if ("XMLHttpRequest".equalsIgnoreCase(rc.request().getHeader("X-Requested-With"))) {
                    rc.addHeadersEndHandler(v -> {
                        if (rc.response().getStatusCode() == 302) {
                            rc.response().setStatusCode(401);
                            rc.response().headers().remove("Location");
                        }
                    });
                }
                rc.next();
            }
        });
    }
}
