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
import jakarta.persistence.PersistenceException;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.InternalServerErrorException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;

@Provider
@Idempotent
@ApplicationScoped
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static final Logger log = Logger.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROPERTY_IDEMPOTENCY_KEY = "internal.idempotency.key";
    private static final String PROPERTY_IDEMPOTENCY_USER = "internal.idempotency.user";
    private static final String PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED = "internal.idempotency.lock.acquired";
    private static final String PROPERTY_IDEMPOTENCY_PATH = "internal.idempotency.path";
    private static final String QUERY_IDEMPOTENCY_KEY_USER_AND_PATH = "idempotencyKey = ?1 and user.id = ?2 and requestPath = ?3";

    @ConfigProperty(name = "povzetkar.idempotency.max-key-length", defaultValue = "128")
    int maxKeyLength;
    
    @ConfigProperty(name = "povzetkar.idempotency.max-payload-size", defaultValue = "10485760") // 10MB
    int maxPayloadSize;

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

        if (idempotencyKey.length() > maxKeyLength) {
            throw new BadRequestException("Idempotency-Key exceeds maximum length of " + maxKeyLength + " characters");
        }

        User user = userContextService.getCurrentUser().orElse(null);
        if (user == null || user.id == null) {
            return; // User is not authenticated, bypass idempotency
        }

        requestContext.setProperty(PROPERTY_IDEMPOTENCY_KEY, idempotencyKey);
        requestContext.setProperty(PROPERTY_IDEMPOTENCY_USER, user.id); // store ID to avoid detached entity issues

        String requestPath = requestContext.getUriInfo().getPath();
        if (requestPath.length() > 1 && requestPath.endsWith("/")) {
            requestPath = requestPath.substring(0, requestPath.length() - 1);
        }
        requestContext.setProperty(PROPERTY_IDEMPOTENCY_PATH, requestPath);

        String requestHash = extractAndHashBody(requestContext);

        boolean lockAcquiredOrServed = attemptLockOrServe(requestContext, idempotencyKey, user.id, requestHash, requestPath);
        if (!lockAcquiredOrServed) {
             throw new WebApplicationException("Conflict or concurrent request processing for this Idempotency-Key", Response.Status.CONFLICT);
        }
    }

    private String extractAndHashBody(ContainerRequestContext requestContext) throws IOException {
        InputStream is = requestContext.getEntityStream();
        if (is == null) {
            return null;
        }

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestInputStream = new DigestInputStream(is, digest);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            
            byte[] buffer = new byte[8192];
            int totalBytesRead = 0;
            int bytesRead;
            
            while ((bytesRead = digestInputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxPayloadSize) {
                    throw new BadRequestException("Request payload exceeds maximum allowed size for idempotency check.");
                }
                baos.write(buffer, 0, bytesRead);
            }
            
            byte[] bytes = baos.toByteArray();
            requestContext.setEntityStream(new ByteArrayInputStream(bytes));
            if (bytes.length == 0) {
                return null;
            }
            
            byte[] hashBytes = digest.digest();
            return Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("Failed to initialize SHA-256 algorithm", e);
        }
    }

    private boolean attemptLockOrServe(ContainerRequestContext requestContext, String idempotencyKey, Long userId, String requestHash, String requestPath) {
        return QuarkusTransaction.requiringNew().call(() -> {
            User managedUser = User.findById(userId);
            if (managedUser == null) return false;

            IdempotentRequest existingRequest = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_KEY_USER_AND_PATH, idempotencyKey, userId, requestPath).firstResult();

            if (existingRequest != null) {
                if (!Objects.equals(requestHash, existingRequest.requestHash)) {
                    log.warn("Idempotency key reused with different payload");
                    return false;
                }

                if (existingRequest.responseStatus != null) {
                    log.info("Idempotency cache hit");
                    serveCachedResponse(requestContext, existingRequest);
                    return true;
                } else {
                    log.info("Idempotency key is currently processing");
                    return false;
                }
            }

            try {
                IdempotentRequest newRequest = new IdempotentRequest();
                newRequest.idempotencyKey = idempotencyKey;
                newRequest.user = managedUser;
                newRequest.requestHash = requestHash;
                newRequest.requestPath = requestPath;
                newRequest.persistAndFlush();
                requestContext.setProperty(PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED, Boolean.TRUE);
                return true;
            } catch (PersistenceException e) {
                log.warn("Race condition encountered when persisting idempotent request lock", e);
                return false;
            }
        });
    }

    private void serveCachedResponse(ContainerRequestContext requestContext, IdempotentRequest cachedRequest) {
        Response.ResponseBuilder responseBuilder = Response.status(cachedRequest.responseStatus);
        
        if (cachedRequest.responseLocation != null) {
            responseBuilder.location(URI.create(cachedRequest.responseLocation));
        }

        if (cachedRequest.responseBody != null) {
            try {
                JsonNode jsonNode = objectMapper.readTree(cachedRequest.responseBody);
                responseBuilder.entity(jsonNode).type(MediaType.APPLICATION_JSON_TYPE);
            } catch (JsonProcessingException e) {
                log.error("Failed to parse cached response body for idempotency key", e);
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
        String requestPath = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_PATH);

        if (idempotencyKey == null || userId == null || requestPath == null || !Boolean.TRUE.equals(lockAcquired)) {
            return; // No key, user, or we are not the lock owner
        }

        int status = responseContext.getStatus();
        
        try {
            if (isSuccessful(status)) {
                updateLockWithResponse(idempotencyKey, userId, requestPath, status, responseContext);
            } else {
                removeLock(idempotencyKey, userId, requestPath);
            }
        } catch (Exception e) {
            log.error("Failed to process response for idempotency cache for key: " + idempotencyKey, e);
            // Attempt to remove the lock to prevent it from becoming stale.
            try {
                removeLock(idempotencyKey, userId, requestPath);
            } catch (Exception cleanupEx) {
                log.error("Failed to remove stale idempotency lock for key: " + idempotencyKey, cleanupEx);
                e.addSuppressed(cleanupEx);
            }
            // Re-throw to ensure the client receives an error.
            throw new InternalServerErrorException("Failed to process response for idempotency cache", e);
        }
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private void updateLockWithResponse(String idempotencyKey, Long userId, String requestPath, int status, ContainerResponseContext responseContext) throws JsonProcessingException {
        String responseBody = extractResponseBody(responseContext);
        String locationStr = extractLocation(responseContext);

        QuarkusTransaction.requiringNew().run(() -> {
            IdempotentRequest request = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_KEY_USER_AND_PATH, idempotencyKey, userId, requestPath).firstResult();
            
            if (request != null) {
                request.responseStatus = status;
                request.responseBody = responseBody;
                request.responseLocation = locationStr;
                request.persist();
            }
        });
    }

    private void removeLock(String idempotencyKey, Long userId, String requestPath) {
        QuarkusTransaction.requiringNew().run(() -> IdempotentRequest.delete(QUERY_IDEMPOTENCY_KEY_USER_AND_PATH, idempotencyKey, userId, requestPath));
    }
    
    private String extractResponseBody(ContainerResponseContext responseContext) throws JsonProcessingException {
        Object entity = responseContext.getEntity();
        if (entity != null) {
            return objectMapper.writeValueAsString(entity);
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
