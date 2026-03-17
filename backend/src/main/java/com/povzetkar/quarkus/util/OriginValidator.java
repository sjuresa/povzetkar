package com.povzetkar.quarkus.util;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class OriginValidator {

    @ConfigProperty(name = "povzetkar.cors.allowed-origins")
    List<String> allowedOrigins;

    private Set<String> normalizedOrigins;

    @PostConstruct
    public void init() {
        if (allowedOrigins != null) {
            normalizedOrigins = allowedOrigins.stream()
                    .map(String::trim)
                    .map(s -> s.replaceAll("/$", "").toLowerCase())
                    .collect(Collectors.toSet());
        } else {
            normalizedOrigins = new HashSet<>();
        }
    }

    public boolean isAllowed(String origin) {
        if (origin == null) {
            return false;
        }
        String normalizedOrigin = origin.replaceAll("/$", "").toLowerCase();
        return normalizedOrigins.contains(normalizedOrigin);
    }
}
