package com.povzetkar.quarkus.config;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

@ApplicationScoped
public class AppDetails {

    @ConfigProperty(name = "quarkus.application.version")
    String version;

    public String getVersion() {
        return version;
    }
}
