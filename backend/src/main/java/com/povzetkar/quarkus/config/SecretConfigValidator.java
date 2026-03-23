package com.povzetkar.quarkus.config;

import io.quarkus.runtime.Quarkus;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.config.ConfigProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * Validates that all required secret configuration properties are present at startup.
 * Fails fast with a clear error message if any are missing, preventing partial system failures
 * at runtime when a service first attempts to use an unconfigured secret.
 */
@ApplicationScoped
public class SecretConfigValidator {

    private static final Logger logger = LoggerFactory.getLogger(SecretConfigValidator.class);

    /**
     * List of config keys that MUST be set to a non-blank value for the application to function.
     * Each entry is a property name as defined in application.properties.
     */
    static final List<String> REQUIRED_SECRETS = List.of(
            "youtube.api.key",
            "google.api.keys",
            "quarkus.datasource.password",
            "quarkus.oidc.client-id",
            "quarkus.oidc.credentials.secret"
    );

    void onStart(@Observes StartupEvent ev) {
        validate(ConfigProvider.getConfig());
    }

    void validate(Config config) {
        List<String> missing = new ArrayList<>();

        for (String key : REQUIRED_SECRETS) {
            var value = config.getOptionalValue(key, String.class);
            if (value.isEmpty() || value.get().isBlank()) {
                missing.add(key);
            }
        }

        if (!missing.isEmpty()) {
            logger.error("FATAL: Required secret configuration is missing or blank. "
                    + "The application cannot start without these values. "
                    + "Missing keys: {}", missing);
            Quarkus.asyncExit(1);
        } else {
            logger.info("All required secret configuration keys are present.");
        }
    }
}
