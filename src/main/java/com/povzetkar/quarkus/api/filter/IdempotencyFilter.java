package com.povzetkar.quarkus.api.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.povzetkar.quarkus.api.dto.ApiEnvelopeDTO;
import com.povzetkar.quarkus.api.dto.ApiErrorDTO;
import com.povzetkar.quarkus.model.IdempotentRequest;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@Provider
@Idempotent
@ApplicationScoped
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = Logger.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROPERTY_IDEMPOTENCY_KEY = "internal.idempotency.key";
    private static final String PROPERTY_IDEMPOTENCY_USER = "internal.idempotency.user";
    private static final String PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED = "internal.idempotency.lock.acquired";
    private static final int MAX_KEY_LENGTH = 128;
    private static final int MAX_PAYLOAD_SIZE = 10 * 1024 * 1024; // 10MB
    private static final String QUERY_IDEMPOTENCY_KEY_AND_USER = "idempotencyKey = ?1 and user.id = ?2";

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
        if (user == null || user.id == null) {
            return; // User is not authenticated, bypass idempotency
        }

        requestContext.setProperty(PROPERTY_IDEMPOTENCY_KEY, idempotencyKey);
        requestContext.setProperty(PROPERTY_IDEMPOTENCY_USER, user.id); // store ID to avoid detached entity issues

        String requestHash = extractAndHashBody(requestContext);

        boolean lockAcquiredOrServed = attemptLockOrServe(requestContext, idempotencyKey, user.id, requestHash);
        if (!lockAcquiredOrServed) {
             // Abort if unable to lock and didn't serve cached response (e.g. processing or conflict)
             requestContext.abortWith(Response.status(Response.Status.CONFLICT)
                     .entity(ApiEnvelopeDTO.error(new ApiErrorDTO(
                             null, "Conflict", 409, "Conflict or concurrent request processing for this Idempotency-Key", "CONCURRENT_REQUEST", null)))
                     .type(MediaType.APPLICATION_JSON_TYPE)
                     .build());
        }
    }

    private String extractAndHashBody(ContainerRequestContext requestContext) throws IOException {
        InputStream is = requestContext.getEntityStream();
        if (is == null) {
            return null;
        }
        byte[] bytes = is.readNBytes(MAX_PAYLOAD_SIZE + 1);
        if (bytes.length > MAX_PAYLOAD_SIZE) {
            throw new BadRequestException("Request payload exceeds maximum allowed size for idempotency check.");
        }
        requestContext.setEntityStream(new ByteArrayInputStream(bytes));
        if (bytes.length == 0) {
            return null;
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(bytes);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("Failed to initialize SHA-256 algorithm", e);
        }
    }

    private boolean attemptLockOrServe(ContainerRequestContext requestContext, String idempotencyKey, Long userId, String requestHash) {
        return QuarkusTransaction.requiringNew().call(() -> {
            User managedUser = User.findById(userId);
            if (managedUser == null) return false;

            IdempotentRequest existingRequest = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_KEY_AND_USER, idempotencyKey, userId).firstResult();

            if (existingRequest != null) {
                if (!java.util.Objects.equals(requestHash, existingRequest.requestHash)) {
                    log.warnf("Idempotency key %s reused with different payload", idempotencyKey);
                    return false;
                }

                if (existingRequest.responseStatus != null) {
                    log.infof("Idempotency cache hit for key: %s", idempotencyKey);
                    serveCachedResponse(requestContext, existingRequest, idempotencyKey);
                    return true;
                } else {
                    log.infof("Idempotency key %s is currently processing", idempotencyKey);
                    return false;
                }
            }

            try {
                IdempotentRequest newRequest = new IdempotentRequest();
                newRequest.idempotencyKey = idempotencyKey;
                newRequest.user = managedUser;
                newRequest.requestHash = requestHash;
                newRequest.persistAndFlush();
                requestContext.setProperty(PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED, Boolean.TRUE);
                return true;
            } catch (PersistenceException _) {
                log.warnf("Race condition encountered when persisting idempotent request lock for key: %s.", idempotencyKey);
                return false;
            }
        });
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
                throw new InternalServerErrorException("Failed to process cached response.", e);
            }
        }

        requestContext.abortWith(responseBuilder.build());
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) throws IOException {
        String idempotencyKey = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_KEY);
        Long userId = (Long) requestContext.getProperty(PROPERTY_IDEMPOTENCY_USER);
        Boolean lockAcquired = (Boolean) requestContext.getProperty(PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED);

        if (idempotencyKey == null || userId == null || !Boolean.TRUE.equals(lockAcquired)) {
            return; // No key, user, or we are not the lock owner
        }

        int status = responseContext.getStatus();
        
        if (isSuccessful(status)) {
            updateLockWithResponse(idempotencyKey, userId, status, responseContext);
        } else {
            removeLock(idempotencyKey, userId);
        }
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private void updateLockWithResponse(String idempotencyKey, Long userId, int status, ContainerResponseContext responseContext) {
        String responseBody = extractResponseBody(responseContext, idempotencyKey);
        String locationStr = extractLocation(responseContext);

        QuarkusTransaction.requiringNew().run(() -> {
            IdempotentRequest request = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_KEY_AND_USER, idempotencyKey, userId).firstResult();
            
            if (request != null) {
                request.responseStatus = status;
                request.responseBody = responseBody;
                request.responseLocation = locationStr;
                request.persist();
            }
        });
    }

    private void removeLock(String idempotencyKey, Long userId) {
        QuarkusTransaction.requiringNew().run(() -> IdempotentRequest.delete(QUERY_IDEMPOTENCY_KEY_AND_USER, idempotencyKey, userId));
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
}
