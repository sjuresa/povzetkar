package com.povzetkar.quarkus.api.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.povzetkar.quarkus.model.IdempotentRequest;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
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
import jakarta.ws.rs.BadRequestException;
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
    private static final String PROPERTY_IDEMPOTENCY_USER = "internal.idempotency.user";
    private static final int MAX_KEY_LENGTH = 128;

    private final ObjectMapper objectMapper;
    private final UserContextService userContextService;

    @Inject
    public IdempotencyFilter(ObjectMapper objectMapper, UserContextService userContextService) {
        this.objectMapper = objectMapper;
        this.userContextService = userContextService;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String idempotencyKey = requestContext.getHeaderString(IDEMPOTENCY_KEY_HEADER);

        if (idempotencyKey == null || idempotencyKey.trim().isEmpty()) {
            return; // No key provided, bypass idempotency
        }

        if (idempotencyKey.length() > MAX_KEY_LENGTH) {
            throw new BadRequestException("Idempotency-Key exceeds maximum length of " + MAX_KEY_LENGTH + " characters");
        }

        User user = userContextService.getCurrentUser().orElse(null);
        if (user == null) {
            return; // User is not authenticated, bypass idempotency
        }

        requestContext.setProperty(PROPERTY_IDEMPOTENCY_KEY, idempotencyKey);
        requestContext.setProperty(PROPERTY_IDEMPOTENCY_USER, user);

        IdempotentRequest cachedRequest = QuarkusTransaction.requiringNew().call(() ->
                IdempotentRequest.<IdempotentRequest>find("idempotencyKey = ?1 and user = ?2", idempotencyKey, user).firstResult()
        );

        if (cachedRequest != null && cachedRequest.responseStatus != null) {
            log.infof("Idempotency cache hit for key: %s", idempotencyKey);
            serveCachedResponse(requestContext, cachedRequest, idempotencyKey);
        }
    }

    private void serveCachedResponse(ContainerRequestContext requestContext, IdempotentRequest cachedRequest, String idempotencyKey) {
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

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String idempotencyKey = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_KEY);
        User user = (User) requestContext.getProperty(PROPERTY_IDEMPOTENCY_USER);

        if (idempotencyKey == null || user == null) {
            return; // No key or user was present in the request
        }

        int status = responseContext.getStatus();
        
        if (isSuccessful(status)) {
            cacheResponse(idempotencyKey, user, status, responseContext);
        }
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private void cacheResponse(String idempotencyKey, User user, int status, ContainerResponseContext responseContext) {
        String responseBody = extractResponseBody(responseContext, idempotencyKey);
        String locationStr = extractLocation(responseContext);

        persistIdempotentRequestAsync(idempotencyKey, user, status, responseBody, locationStr);
    }
    
    private String extractResponseBody(ContainerResponseContext responseContext, String idempotencyKey) {
        Object entity = responseContext.getEntity();
        if (entity != null) {
            try {
                return objectMapper.writeValueAsString(entity);
            } catch (JsonProcessingException e) {
                log.errorf(e, "Failed to serialize response body for idempotency key: %s", idempotencyKey);
            }
        }
        return null;
    }

    private String extractLocation(ContainerResponseContext responseContext) {
        if (responseContext.getLocation() != null) {
            return responseContext.getLocation().toString();
        }
        return null;
    }

    private void persistIdempotentRequestAsync(String idempotencyKey, User user, int status, String responseBody, String locationStr) {
        QuarkusTransaction.requiringNew().run(() -> {
            if (IdempotentRequest.find("idempotencyKey = ?1 and user = ?2", idempotencyKey, user).firstResult() != null) {
                return; // Another request already cached the response.
            }

            try {
                IdempotentRequest request = new IdempotentRequest();
                request.idempotencyKey = idempotencyKey;
                request.user = user;
                request.responseStatus = status;
                request.responseBody = responseBody;
                request.responseLocation = locationStr;
                request.persistAndFlush(); 
            } catch (jakarta.persistence.PersistenceException _) {
                log.warnf("Race condition encountered when persisting idempotent request for key: %s. Another request likely succeeded.", idempotencyKey);
            }
        });
    }
}
