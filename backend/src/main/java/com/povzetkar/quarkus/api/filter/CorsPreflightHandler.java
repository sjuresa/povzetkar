package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.util.OriginValidator;
import io.quarkus.runtime.StartupEvent;
import io.vertx.core.http.HttpMethod;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
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

    @ConfigProperty(name = "povzetkar.cors.allowed-headers")
    String allowedHeaders;

    @ConfigProperty(name = "povzetkar.cors.allowed-methods")
    String allowedMethods;

    @Inject
    OriginValidator originValidator;

    void onStart(@Observes StartupEvent ev) {
        router.route().order(-10000).handler(this::handleCorsRequest);
    }

    private void handleCorsRequest(RoutingContext rc) {
        String origin = rc.request().getHeader("Origin");

        if (!originValidator.isAllowed(origin)) {
            rc.next();
            return;
        }

        setupCorsResponseHeaders(rc, origin);

        if (rc.request().method() == HttpMethod.OPTIONS) {
            handlePreflightResponse(rc, origin);
        } else {
            handleAjaxRedirect(rc);
            rc.next();
        }
    }


    private void setupCorsResponseHeaders(RoutingContext rc, String origin) {
        rc.addHeadersEndHandler(v -> {
            if (!rc.response().headers().contains("Access-Control-Allow-Origin")) {
                rc.response().putHeader("Access-Control-Allow-Origin", origin);
                rc.response().putHeader("Access-Control-Allow-Credentials", "true");
            }
        });
    }

    private void handlePreflightResponse(RoutingContext rc, String origin) {
        rc.response()
                .putHeader("Access-Control-Allow-Origin", origin)
                .putHeader("Access-Control-Allow-Methods", allowedMethods)
                .putHeader("Access-Control-Allow-Headers", allowedHeaders)
                .putHeader("Access-Control-Max-Age", "3600")
                .putHeader("Access-Control-Allow-Credentials", "true")
                .setStatusCode(200)
                .end();
    }

    private void handleAjaxRedirect(RoutingContext rc) {
        if ("XMLHttpRequest".equalsIgnoreCase(rc.request().getHeader("X-Requested-With"))) {
            rc.addHeadersEndHandler(v -> {
                if (rc.response().getStatusCode() == 302) {
                    rc.response().setStatusCode(401);
                    rc.response().headers().remove("Location");
                }
            });
        }
    }
}
