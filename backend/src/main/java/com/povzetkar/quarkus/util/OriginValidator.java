package com.povzetkar.quarkus.util;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OriginValidator {

    private static final Logger logger = LoggerFactory.getLogger(OriginValidator.class);

    @ConfigProperty(name = "povzetkar.cors.allowed-origins")
    List<String> allowedOrigins;

    private Set<String> normalizedOrigins;

    @PostConstruct
    public void init() {
        normalizedOrigins = allowedOrigins.stream()
                .filter(origin -> origin != null && !origin.isBlank())
                .map(this::normalize)
                .collect(Collectors.toSet());
    }

    public boolean isAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        return normalizedOrigins.contains(normalize(origin));
    }

    private String normalize(String origin) {
        if (origin == null) {
            return null;
        }
        try {
            URI uri = new URI(origin.trim());
            if (uri.getScheme() != null && uri.getHost() != null) {
                int port = uri.getPort();
                if ((port == 80 && "http".equalsIgnoreCase(uri.getScheme())) || (port == 443 && "https".equalsIgnoreCase(uri.getScheme()))) {
                    port = -1; // Omit default port
                }
                return new URI(uri.getScheme(), null, uri.getHost(), port, null, null, null).toString().toLowerCase();
            }
        } catch (URISyntaxException e) {
            // Fallback for malformed or non-standard origins
            logger.warn("Malformed origin URI '{}', using fallback normalization.", origin.trim(), e);
        }
        // Fallback to simple normalization for non-hierarchical URIs or on error
        String trimmed = origin.trim();
        return (trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed).toLowerCase();
    }
}
