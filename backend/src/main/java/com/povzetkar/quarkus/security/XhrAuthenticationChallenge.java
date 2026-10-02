package com.povzetkar.quarkus.security;

import io.quarkus.oidc.JavaScriptRequestChecker;
import io.quarkus.vertx.http.runtime.security.ChallengeData;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Answers an unauthenticated XHR with 401 instead of the 302 to Google (POV-221). With
 * quarkus.oidc.authentication.java-script-auto-redirect=false the OIDC mechanism asks this checker whether a
 * request comes from JavaScript and, if so, sends its challenge; a browser navigation keeps the redirect. The
 * SPA's Axios client sends X-Requested-With: XMLHttpRequest and handles 401 (session expired).
 */
@ApplicationScoped
public class XhrAuthenticationChallenge implements JavaScriptRequestChecker {

    private static final String REQUESTED_WITH = "X-Requested-With";

    /** Same recognition as Quarkus' default without a checker bean. */
    @Override
    public boolean isJavaScriptRequest(RoutingContext context) {
        String requestedWith = context.request().getHeader(REQUESTED_WITH);
        return "XMLHttpRequest".equals(requestedWith) || "JavaScript".equals(requestedWith);
    }

    /** 401 instead of Quarkus' default 499, which the SPA and HTTP clients would not treat as "log in again". */
    @Override
    public ChallengeData getChallenge(RoutingContext context) {
        return new ChallengeData(401, "WWW-Authenticate", "OIDC");
    }
}
