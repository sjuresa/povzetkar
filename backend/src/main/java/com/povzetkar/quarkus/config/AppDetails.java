package com.povzetkar.quarkus.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AppDetails {

    private final String version;

    public AppDetails(@ConfigProperty(name = "quarkus.application.version") String version) {
        this.version = version;
    }

    public String getVersion() {
        return version;
    }
}
