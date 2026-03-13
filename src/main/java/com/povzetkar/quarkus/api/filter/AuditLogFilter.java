package com.povzetkar.quarkus.api.filter;

import com.povzetkar.quarkus.model.AuditLog;
import com.povzetkar.quarkus.security.UserSecurityDetails;
import com.povzetkar.quarkus.service.UserContextService;
import com.povzetkar.quarkus.util.StructuredLogger;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.security.identity.SecurityIdentity;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

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

    private static final StructuredLogger  logger = StructuredLogger.getLogger(AuditLogFilter.class);
    private static final String PROPERTY_START_TIME = "internal.audit.startTime";
    private static final int USER_AGENT_MAX_LENGTH = 512;

    private final SecurityIdentity securityIdentity;
    private final UserContextService userContextService;

    @Inject
    public AuditLogFilter(SecurityIdentity securityIdentity, UserContextService userContextService) {
        this.securityIdentity = securityIdentity;
        this.userContextService = userContextService;
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
        if (startTime == null) return;

        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        String correlationId = CorrelationIdFilter.currentCorrelationId();
        String requestPath = getNormalizedPath(requestContext);
        String userAgent = getBoundedUserAgent(requestContext);

        UserSecurityDetails security = UserSecurityDetails.capture(securityIdentity);

        persistAuditLogAsync(correlationId, security, requestContext.getMethod(),
                responseContext.getStatus(), requestPath, durationMs, userAgent);
    }

    private String getNormalizedPath(ContainerRequestContext requestContext) {
        String path = requestContext.getUriInfo().getPath();
        return path.length() > 1 && path.endsWith("/") ? path.substring(0, path.length() - 1) : path;
    }

    private String getBoundedUserAgent(ContainerRequestContext requestContext) {
        String agent = requestContext.getHeaderString("User-Agent");
        return agent != null && agent.length() > USER_AGENT_MAX_LENGTH ? agent.substring(0, USER_AGENT_MAX_LENGTH) : agent;
    }

    private void persistAuditLogAsync(String correlationId, UserSecurityDetails security, String httpMethod,
                                     int status, String path, long durationMs, String userAgent) {
        Infrastructure.getDefaultExecutor().execute(() -> {
            try {
                final Long userId = userContextService.findUserId(security);
                QuarkusTransaction.requiringNew().run(() ->
                        new AuditLog(correlationId, userId, httpMethod, path, status, durationMs, userAgent).persist()
                );
            } catch (Exception e) {
                logger.warn(e, "event", "audit_log_persistence_failed", "httpMethod", httpMethod, "path", path);
            }
        });
    }
}
