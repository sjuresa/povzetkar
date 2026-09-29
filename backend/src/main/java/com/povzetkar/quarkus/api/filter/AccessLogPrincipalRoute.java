package com.povzetkar.quarkus.api.filter;

import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.http.runtime.security.QuarkusHttpUser;
import io.vertx.ext.auth.User;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.jwt.JsonWebToken;

import java.security.Principal;
import java.util.regex.Pattern;

/**
 * Supplies the user field of the access log (POV-214, ADR-0012), read by the pattern as
 * {@code %{d,povzetkar.log-user}}.
 * <p>
 * {@code %u} is not used: Quarkus' Google provider makes the principal the display {@code name} — text the user
 * controls, with spaces and quotes that shift the fields after it and let a user forge a log line. Logged instead:
 * the Google subject for OIDC identities (maps to {@code user_identities.external_id}), the principal name
 * otherwise (the mapped e-mail for API keys) — restricted to a safe character set in both cases.
 * <p>
 * Set when the response headers are written: authentication has run by then, also for streamed responses (SSE),
 * and the access log reads it when the request is done.
 */
@ApplicationScoped
public class AccessLogPrincipalRoute {

    public static final String DATA_KEY = "povzetkar.log-user";

    private static final Pattern UNSAFE = Pattern.compile("[^A-Za-z0-9@._+-]");
    private static final int MAX_LENGTH = 128;

    void register(@Observes Router router) {
        router.route().order(Integer.MIN_VALUE + 1).handler(rc -> {
            rc.addHeadersEndHandler(v -> {
                String user = logUser(rc.user());
                if (user != null) {
                    rc.put(DATA_KEY, user);
                }
            });
            rc.next();
        });
    }

    /** The value to log for this user, or {@code null} for an anonymous request. */
    static String logUser(User user) {
        if (!(user instanceof QuarkusHttpUser quarkusUser)) {
            return null;
        }
        SecurityIdentity identity = quarkusUser.getSecurityIdentity();
        if (identity == null || identity.isAnonymous()) {
            return null;
        }
        Principal principal = identity.getPrincipal();
        String value = principal instanceof JsonWebToken jwt ? jwt.getSubject() : principal.getName();
        if (value == null || value.isBlank()) {
            return null;
        }
        String safe = UNSAFE.matcher(value).replaceAll("_");
        return safe.length() > MAX_LENGTH ? safe.substring(0, MAX_LENGTH) : safe;
    }
}
