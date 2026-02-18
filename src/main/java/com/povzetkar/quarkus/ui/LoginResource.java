package com.povzetkar.quarkus.ui;

import io.quarkus.qute.Location;
import io.quarkus.qute.Template;
import io.quarkus.security.identity.SecurityIdentity;
import io.quarkus.vertx.web.Route;
import io.quarkus.vertx.web.Route.HttpMethod;
import io.vertx.ext.web.RoutingContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class LoginResource {

    private final Template login;
    private final SecurityIdentity securityIdentity;
    private final String logoutPath;

    @Inject
    public LoginResource(@Location("login.html") Template login,
            SecurityIdentity securityIdentity,
            @ConfigProperty(name = "quarkus.oidc.logout.path", defaultValue = "/logout") String logoutPath) {
        this.login = login;
        this.securityIdentity = securityIdentity;
        this.logoutPath = logoutPath;
    }

    @Route(path = "/login", methods = HttpMethod.GET)
    public void login(RoutingContext context) {
        if (!securityIdentity.isAnonymous()) {
            context.response().setStatusCode(302).putHeader("Location", "/ui/topics").end();
            return;
        }
        context.response().putHeader("Content-Type", "text/html; charset=utf-8").end(login.render());
    }

    @Route(path = "/logout", methods = HttpMethod.GET)
    public void logout(RoutingContext context) {
        // Determine where to redirect after logout.
        // If we just redirect to the logout path (which is likely a POST handled by
        // OIDC),
        // we might need to do a client-side post or use a library.
        // However, standard OIDC logout often supports GET with a redirect_uri.
        // But Quarkus OIDC implementation of /logout clears the session cookie.
        // To trigger it via GET, we can just redirect to it if it supports GET,
        // but default is often POST.

        // Let's assume we want to clear the local session.
        // If we are using valid OIDC, we should redirect to the IDP's logout endpoint?
        // Quarkus handles this if we hit the logout endpoint.

        // Simpler approach: Create a simple form in the response that auto-submits to
        // /logout?
        // Or just rely on the UI having a proper Logout button (POST).

        // But the requirement says "Logout functionality is implemented".
        // I'll implement a route that clears the session if possible, or redirects.
        // For now, I'll serve a page that auto-submits a POST to /logout or just 302 to
        // /logout if GET is allowed.
        // Let's try 302 to /logout. If it fails (405 Method Not Allowed), we'll know.
        // Actually, most modern security setups require POST for logout to prevent
        // CSRF.

        // So I will make the Logout link in the UI a form button, not a simple GET
        // link.
        // This route might not be needed if the UI handles it correctly.
        // But I'll keep a GET /logout for convenience if someone types it, which
        // renders a "Click to logout" button.

        String html = String.format("""
                <html>
                <body onload="document.forms[0].submit()">
                    <form action="%s" method="POST">
                        <button type="submit">Logging out...</button>
                    </form>
                </body>
                </html>
                """, logoutPath);
        context.response().putHeader("Content-Type", "text/html").end(html);
    }

    @Route(path = "/", methods = HttpMethod.GET)
    public void index(RoutingContext context) {
        context.response().setStatusCode(302).putHeader("Location", "/login").end();
    }
}
