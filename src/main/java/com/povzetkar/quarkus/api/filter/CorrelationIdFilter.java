package com.povzetkar.quarkus.api.filter;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.MDC;

import java.io.IOException;
import java.util.UUID;

/**
 * JAX-RS filter that injects a Correlation ID into the MDC for every request.
 * If the client provides an {@code X-Correlation-ID} header, that value is used.
 * Otherwise a new UUID is generated. The ID is also echoed back to the client
 * in the response headers.
 *
 * <p>This filter runs on ALL requests (no {@code @NameBinding}). It is responsible
 * for setting up and tearing down the {@code correlationId} MDC key.</p>
 *
 * <p>To propagate the correlation ID to background threads, use
 * {@link #currentCorrelationId()} to capture the ID before dispatching the thread,
 * then call {@link #setCorrelationId(String)} inside the thread and
 * {@link #clearCorrelationId()} in a {@code finally} block.</p>
 */
@Provider
@ApplicationScoped
public class CorrelationIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String MDC_CORRELATION_ID = "correlationId";
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    private static final String PROPERTY_CORRELATION_ID = "internal.correlationId";

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String correlationId = requestContext.getHeaderString(HEADER_CORRELATION_ID);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        } else if (correlationId.length() > 36) {
            correlationId = correlationId.substring(0, 36);
        }

        // Store for response filter to read
        requestContext.setProperty(PROPERTY_CORRELATION_ID, correlationId);

        // Inject into MDC so all log statements for this request include the ID
        MDC.put(MDC_CORRELATION_ID, correlationId);
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String correlationId = (String) requestContext.getProperty(PROPERTY_CORRELATION_ID);
        if (correlationId != null) {
            // Echo back to the client
            responseContext.getHeaders().putSingle(HEADER_CORRELATION_ID, correlationId);
        }
        // Clean up MDC to prevent leaking into any thread-pool-recycled threads
        MDC.remove(MDC_CORRELATION_ID);
    }

    /**
     * Returns the current correlation ID from the MDC, or {@code null} if not set.
     * Intended to be called on the request thread before dispatching a background task.
     */
    public static String currentCorrelationId() {
        Object value = MDC.get(MDC_CORRELATION_ID);
        return value != null ? value.toString() : null;
    }

    /**
     * Sets the correlation ID in the MDC on the current thread.
     * Intended to be called at the beginning of a background thread.
     */
    public static void setCorrelationId(String correlationId) {
        if (correlationId != null) {
            MDC.put(MDC_CORRELATION_ID, correlationId);
        }
    }

    /**
     * Clears the correlation ID from the MDC on the current thread.
     * Must be called in a {@code finally} block in background threads.
     */
    public static void clearCorrelationId() {
        MDC.remove(MDC_CORRELATION_ID);
    }
}
