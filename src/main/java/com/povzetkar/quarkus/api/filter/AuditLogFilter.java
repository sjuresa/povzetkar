package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.model.AuditLog;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.IOException;

/**
 * JAX-RS filter that persists an {@link AuditLog} record for every API request
 * under {@code /api/v1/}. It records the HTTP method, path, response status,
 * duration in milliseconds, user agent, correlation ID, and the authenticated
 * user's ID (if known).
 *
 * <p>This filter intentionally persists audit records directly via Panache in a
 * {@code QuarkusTransaction.requiringNew()} block to avoid coupling to the business
 * service layer and to capture the final HTTP response status.</p>
 */
@Provider
@ApplicationScoped
public class AuditLogFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger logger = LoggerFactory.getLogger(AuditLogFilter.class);
    private static final String PROPERTY_START_TIME = "internal.audit.startTime";
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final UserContextService userContextService;
    private final SecurityIdentity securityIdentity;

    @Inject
    public AuditLogFilter(UserContextService userContextService, SecurityIdentity securityIdentity) {
        this.userContextService = userContextService;
        this.securityIdentity = securityIdentity;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String path = requestContext.getUriInfo().getPath();
        if (!path.startsWith("/api/v1")) {
            return; // Only audit API paths
        }
        requestContext.setProperty(PROPERTY_START_TIME, System.nanoTime());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        Long startTime = (Long) requestContext.getProperty(PROPERTY_START_TIME);
        if (startTime == null) {
            return; // Not an audited path — skip
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        String correlationId = CorrelationIdFilter.currentCorrelationId();

        String rawPath = requestContext.getUriInfo().getPath();
        String requestPath = rawPath.length() > 1 && rawPath.endsWith("/")
                ? rawPath.substring(0, rawPath.length() - 1)
                : rawPath;
        String rawUserAgent = requestContext.getHeaderString("User-Agent");
        String userAgent = rawUserAgent != null && rawUserAgent.length() > USER_AGENT_MAX_LENGTH
                ? rawUserAgent.substring(0, USER_AGENT_MAX_LENGTH)
                : rawUserAgent;

        final String httpMethod = requestContext.getMethod();
        final int responseStatus = responseContext.getStatus();

        // Capture security context details
        final boolean isAnonymous = securityIdentity.isAnonymous();

        Long userId = null;
        if (!isAnonymous) {
            userId = userContextService.getCurrentUser().map(u -> u.id).orElse(null);
        }

        final Long finalUserId = userId;
        try {
            QuarkusTransaction.requiringNew().run(() ->
                    new AuditLog(correlationId, finalUserId, httpMethod, requestPath,
                            responseStatus, durationMs, userAgent).persist()
            );
        } catch (Exception e) {
            // Audit failures must never affect the actual response — log and swallow
            logger.warn("Failed to persist audit log entry for [{}] {}", httpMethod, requestPath, e);
        }
    }
}
