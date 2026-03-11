package com.povzetkar.quarkus.api.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.povzetkar.quarkus.model.IdempotentRequest;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.narayana.jta.QuarkusTransaction;
import jakarta.annotation.Nonnull;
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

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.nio.file.attribute.PosixFilePermission;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.Objects;
import java.util.Set;

@Provider
@Idempotent
@ApplicationScoped
public class IdempotencyFilter implements ContainerRequestFilter, ContainerResponseFilter {

    private static class ResponseSizeExceededException extends IOException {}

    private static final Logger log = Logger.getLogger(IdempotencyFilter.class);
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final String PROPERTY_IDEMPOTENCY_KEY = "internal.idempotency.key";
    private static final String PROPERTY_IDEMPOTENCY_USER = "internal.idempotency.user";
    private static final String PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED = "internal.idempotency.lock.acquired";
    private static final String PROPERTY_IDEMPOTENCY_PATH = "internal.idempotency.path";
    private static final String PROPERTY_IDEMPOTENCY_METHOD = "internal.idempotency.method";
    private static final String PROPERTY_IDEMPOTENCY_TEMP_FILE = "internal.idempotency.tempFile";
    private static final String QUERY_IDEMPOTENCY_LOOKUP = "idempotencyKey = ?1 and user.id = ?2 and requestPath = ?3 and requestMethod = ?4";

    @ConfigProperty(name = "povzetkar.idempotency.max-key-length", defaultValue = "128")
    int maxKeyLength;
    
    @ConfigProperty(name = "povzetkar.idempotency.max-payload-size", defaultValue = "10485760") // 10MB
    int maxPayloadSize;

    @ConfigProperty(name = "povzetkar.idempotency.max-response-size", defaultValue = "10485760") // 10MB
    int maxResponseSize;

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

        String requestMethod = requestContext.getMethod();
        requestContext.setProperty(PROPERTY_IDEMPOTENCY_METHOD, requestMethod);

        String requestHash = extractAndHashBody(requestContext);

        boolean lockAcquiredOrServed = attemptLockOrServe(requestContext, idempotencyKey, user.id, requestHash, requestPath, requestMethod);
        if (!lockAcquiredOrServed) {
             throw new WebApplicationException("Conflict or concurrent request processing for this Idempotency-Key", Response.Status.CONFLICT);
        }
    }

    private String sanitizeLogKey(String key) {
        if (key == null) return null;
        return key.replaceAll("\\p{Cntrl}", "_");
    }

    private Path createTempFile() throws IOException {
        try {
            FileAttribute<Set<PosixFilePermission>> attr =
                PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"));
            return Files.createTempFile("idemp-req-", ".tmp", attr);
        } catch (UnsupportedOperationException _) {
            log.debug("POSIX file permissions not supported, created temp file with default permissions.");
            return Files.createTempFile("idemp-req-", ".tmp");
        }
    }

    private String extractAndHashBody(ContainerRequestContext requestContext) throws IOException {
        InputStream is = requestContext.getEntityStream();
        if (is == null) {
            return null;
        }

        try (TempFileResource tfr = new TempFileResource(createTempFile())) {
            Path tempFile = tfr.getPath();
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            DigestInputStream digestInputStream = new DigestInputStream(is, digest);
            requestContext.setProperty(PROPERTY_IDEMPOTENCY_TEMP_FILE, tempFile);
            
            long totalBytesRead = writeStreamToTempFile(digestInputStream, tempFile);
            
            if (totalBytesRead == 0) {
                return null;
            }
            
            requestContext.setEntityStream(Files.newInputStream(tempFile));
            
            byte[] hashBytes = digest.digest();
            String hash = Base64.getUrlEncoder().withoutPadding().encodeToString(hashBytes);
            tfr.keep();
            return hash;
        } catch (NoSuchAlgorithmException e) {
            throw new InternalServerErrorException("Failed to initialize SHA-256 algorithm", e);
        }
    }

    private long writeStreamToTempFile(DigestInputStream digestInputStream, Path tempFile) throws IOException {
        byte[] buffer = new byte[8192];
        long totalBytesRead = 0;
        int bytesRead;
        
        try (java.io.OutputStream os = Files.newOutputStream(tempFile)) {
            while ((bytesRead = digestInputStream.read(buffer)) != -1) {
                totalBytesRead += bytesRead;
                if (totalBytesRead > maxPayloadSize) {
                    throw new BadRequestException("Request payload exceeds maximum allowed size for idempotency check.");
                }
                os.write(buffer, 0, bytesRead);
            }
            return totalBytesRead;
        }
    }

    private boolean attemptLockOrServe(ContainerRequestContext requestContext, String idempotencyKey, Long userId, String requestHash, String requestPath, String requestMethod) {
        return QuarkusTransaction.requiringNew().call(() -> {
            User managedUser = User.findById(userId);
            if (managedUser == null) return false;

            IdempotentRequest existingRequest = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_LOOKUP, idempotencyKey, userId, requestPath, requestMethod).firstResult();

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
                newRequest.requestMethod = requestMethod;
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
        Path tempFile = (Path) requestContext.getProperty(PROPERTY_IDEMPOTENCY_TEMP_FILE);
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                log.warn("Failed to delete temp file for idempotent request", e);
            }
        }

        String idempotencyKey = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_KEY);
        Long userId = (Long) requestContext.getProperty(PROPERTY_IDEMPOTENCY_USER);
        Boolean lockAcquired = (Boolean) requestContext.getProperty(PROPERTY_IDEMPOTENCY_LOCK_ACQUIRED);
        String requestPath = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_PATH);
        String requestMethod = (String) requestContext.getProperty(PROPERTY_IDEMPOTENCY_METHOD);

        if (idempotencyKey == null || userId == null || requestPath == null || requestMethod == null || !Boolean.TRUE.equals(lockAcquired)) {
            return; // No key, user, or we are not the lock owner
        }

        int status = responseContext.getStatus();
        
        try {
            if (isSuccessful(status)) {
                boolean cached = updateLockWithResponse(idempotencyKey, userId, requestPath, requestMethod, status, responseContext);
                if (!cached) {
                    removeLock(idempotencyKey, userId, requestPath, requestMethod); // Response exceeded max size
                }
            } else {
                removeLock(idempotencyKey, userId, requestPath, requestMethod);
            }
        } catch (Exception e) {
            log.error("Failed to process response for idempotency cache for key: " + sanitizeLogKey(idempotencyKey), e);
            // Attempt to remove the lock to prevent it from becoming stale.
            try {
                removeLock(idempotencyKey, userId, requestPath, requestMethod);
            } catch (Exception cleanupEx) {
                log.error("Failed to remove stale idempotency lock for key: " + sanitizeLogKey(idempotencyKey), cleanupEx);
                e.addSuppressed(cleanupEx);
            }
            // Re-throw to ensure the client receives an error.
            throw new InternalServerErrorException("Failed to process response for idempotency cache", e);
        }
    }

    private boolean isSuccessful(int status) {
        return status >= 200 && status < 300;
    }

    private boolean updateLockWithResponse(String idempotencyKey, Long userId, String requestPath, String requestMethod, int status, ContainerResponseContext responseContext) throws IOException {
        String responseBody;
        try {
            responseBody = extractResponseBodySafe(responseContext);
        } catch (IOException e) {
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof ResponseSizeExceededException) {
                    return false;
                }
                cause = cause.getCause();
            }
            throw e;
        }
        
        String locationStr = extractLocation(responseContext);

        QuarkusTransaction.requiringNew().run(() -> {
            IdempotentRequest request = IdempotentRequest.<IdempotentRequest>find(
                    QUERY_IDEMPOTENCY_LOOKUP, idempotencyKey, userId, requestPath, requestMethod).firstResult();
            
            if (request != null) {
                request.responseStatus = status;
                request.responseBody = responseBody;
                request.responseLocation = locationStr;
                request.persist();
            }
        });
        return true;
    }

    private void removeLock(String idempotencyKey, Long userId, String requestPath, String requestMethod) {
        QuarkusTransaction.requiringNew().run(() -> IdempotentRequest.delete(QUERY_IDEMPOTENCY_LOOKUP, idempotencyKey, userId, requestPath, requestMethod));
    }
    
    private String extractResponseBodySafe(ContainerResponseContext responseContext) throws IOException {
        Object entity = responseContext.getEntity();
        if (entity == null) {
            return null;
        }
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            OutputStream boundedOs = new OutputStream() {
                private long count = 0;

                @Override
                public void write(int b) throws IOException {
                    if (count >= maxResponseSize) {
                        throw new ResponseSizeExceededException();
                    }
                    count++;
                    baos.write(b);
                }

                @Override
                public void write(@Nonnull byte[] b, int off, int len) throws IOException {
                    if (len > maxResponseSize - count) {
                        throw new ResponseSizeExceededException();
                    }
                    count += len;
                    baos.write(b, off, len);
                }
            };
            objectMapper.writeValue(boundedOs, entity);
            return baos.toString(java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private String extractLocation(ContainerResponseContext responseContext) {
        if (responseContext.getLocation() != null) {
            return responseContext.getLocation().toString();
        }
        return null;
    }

    private static class TempFileResource implements AutoCloseable {
        private final Path path;
        private boolean keep = false;

        TempFileResource(Path path) {
            this.path = path;
        }

        Path getPath() {
            return path;
        }

        void keep() {
            this.keep = true;
        }

        @Override
        public void close() throws IOException {
            if (!keep) {
                Files.deleteIfExists(path);
            }
        }
    }
}
