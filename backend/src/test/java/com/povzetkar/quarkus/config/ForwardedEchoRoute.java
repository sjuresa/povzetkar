package com.povzetkar.quarkus.config;

import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.HostAndPort;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

/**
 * Test-only route that echoes what the app derives from a request after the proxy handling
 * (POV-214 proxy-trust tests): scheme, host and remote address. Registered on the Router directly —
 * neither JAX-RS nor {@code @Route} — so SmallRye OpenAPI never sees it and the spec drift check stays clean.
 */
@ApplicationScoped
public class ForwardedEchoRoute {

    static final String PATH = "/test-only/forwarded";

    void register(@Observes Router router) {
        router.get(PATH).handler(rc -> {
            HttpServerRequest request = rc.request();
            HostAndPort authority = request.authority();
            rc.response().end(request.scheme() + " " + authority.host() + " " + request.remoteAddress().hostAddress());
        });
    }
}
