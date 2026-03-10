package com.povzetkar.quarkus.api.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.povzetkar.quarkus.model.IdempotentRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.IOException;
import java.net.URI;

@Provider
@Idempotent
@ApplicationScoped
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = Logger.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROPERTY_IDEMPOTENCY_KEY = "internal.idempotency.key";

    private final ObjectMapper objectMapper;

    @Inject
    public IdempotencyFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return; // No key provided, bypass idempotency
        }

        requestContext.setProperty(PROPERTY_IDEMPOTENCY_KEY, idempotencyKey);

        // Check if we have a cached response using QuarkusTransaction for safety
        IdempotentRequest cachedRequest = QuarkusTransaction.joiningExisting().call(() ->
                IdempotentRequest.findById(idempotencyKey)
        );

        if (cachedRequest != null && cachedRequest.responseStatus != null) {
            log.infof("Idempotency cache hit for key: %s", idempotencyKey);
            
            Response.ResponseBuilder responseBuilder = Response.status(cachedRequest.responseStatus);
            
            if (cachedRequest.responseLocation != null) {
                responseBuilder.location(URI.create(cachedRequest.responseLocation));
            }

            if (cachedRequest.responseBody != null) {
                try {
                    JsonNode jsonNode = objectMapper.readTree(cachedRequest.responseBody);
                    responseBuilder.entity(jsonNode).type(MediaType.APPLICATION_JSON_TYPE);
                } catch (JsonProcessingException e) {
                    log.errorf(e, "Failed to parse cached response body for idempotency key: %s", idempotencyKey);
                    responseBuilder.entity(cachedRequest.responseBody).type(MediaType.APPLICATION_JSON_TYPE);
                }
            }

            requestContext.abortWith(responseBuilder.build());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String idempotencyKey = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_KEY);

        if (idempotencyKey == null) {
            return; // No key was present in the request
        }

        int status = responseContext.getStatus();
        
        // We only cache successful responses to allow retries for failures
        if (status >= 200 && status < 300) {
            String responseBody = null;
            Object entity = responseContext.getEntity();
            
            if (entity != null) {
                try {
                    responseBody = objectMapper.writeValueAsString(entity);
                } catch (JsonProcessingException e) {
                    log.errorf(e, "Failed to serialize response body for idempotency key: %s", idempotencyKey);
                }
            }

            String locationStr = null;
            if (responseContext.getLocation() != null) {
                locationStr = responseContext.getLocation().toString();
            }

            final String finalResponseBody = responseBody;
            final String finalLocationStr = locationStr;

            // Persist the response asynchronously or synchronously in a new transaction
            QuarkusTransaction.requiringNew().run(() -> {
                IdempotentRequest request = IdempotentRequest.findById(idempotencyKey);
                if (request == null) {
                    request = new IdempotentRequest();
                    request.idempotencyKey = idempotencyKey;
                    request.responseStatus = status;
                    request.responseBody = finalResponseBody;
                    request.responseLocation = finalLocationStr;
                    request.persist();
                }
            });
        }
    }
}
