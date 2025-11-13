package com.povzetkar.quarkus.ui.util;

import io.vertx.core.Vertx;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.SessionHandler;
import io.vertx.ext.web.sstore.LocalSessionStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class UIRouterSetup {
    public void setup(@Observes Router router, Vertx vertx) {
        router.route("/ui/*").order(-1).handler(BodyHandler.create());
        router.route("/ui/*").order(0).handler(SessionHandler.create(LocalSessionStore.create(vertx)));
    }
}
