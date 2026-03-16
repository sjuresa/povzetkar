package com.povzetkar.quarkus.api.filter;

import io.quarkus.runtime.StartupEvent;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import java.util.List;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Low-level Vert.x handler to catch OPTIONS requests before OIDC interrupts them.
 * This is the most reliable way to handle CORS preflight in Quarkus OIDC web-app mode.
 */
@ApplicationScoped
public class CorsPreflightHandler {

    @Inject
    Router router;

    @ConfigProperty(name = "povzetkar.frontend.origin")
    List<String> allowedOrigins;

    @ConfigProperty(name = "povzetkar.cors.allowed-methods")
    String allowedMethods;

    @ConfigProperty(name = "povzetkar.cors.allowed-headers")
    String allowedHeaders;

    void onStart(@Observes StartupEvent ev) {
        router.route().order(-10000).handler(rc -> {
            String origin = rc.request().getHeader("Origin");

            // Not a request from one of our allowed frontend origins, let other handlers deal with it.
            boolean isAllowed = origin != null && allowedOrigins.stream()
                    .anyMatch(ao -> ao.equalsIgnoreCase(origin));

            if (!isAllowed) {
                rc.next();
                return;
            }
            // From here, we know the request is from our allowed frontend origin.
            // Ensure CORS headers are ALWAYS present on any response to the frontend.
            rc.addHeadersEndHandler(v -> {
                if (!rc.response().headers().contains("Access-Control-Allow-Origin")) {
                    rc.response().putHeader("Access-Control-Allow-Origin", origin);
                    rc.response().putHeader("Access-Control-Allow-Credentials", "true");
                }
            });

            if (rc.request().method() == HttpMethod.OPTIONS) {
                // Handle CORS preflight request.
                rc.response()
                        .putHeader("Access-Control-Allow-Origin", origin)
                        .putHeader("Access-Control-Allow-Methods", allowedMethods)
                        .putHeader("Access-Control-Allow-Headers", allowedHeaders)
                        .putHeader("Access-Control-Max-Age", "3600")
                        .putHeader("Access-Control-Allow-Credentials", "true")
                        .setStatusCode(200)
                        .end();
            } else {
                // For actual AJAX requests, convert OIDC redirects (302) to 401 Unauthorized.
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
