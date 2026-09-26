package com.povzetkar.quarkus.security;

import io.smallrye.config.ConfigMapping;

import java.util.Optional;

/**
 * Configuration of the optional read-only API-key access (POV-205): the single place that
 * decides whether the feature is configured (POV-208). Blank values count as unset.
 */
@ConfigMapping(prefix = "povzetkar.api")
public interface ApiKeyConfig {

    String KEY_PROPERTY = "povzetkar.api.key";
    String USER_EMAIL_PROPERTY = "povzetkar.api.key-user-email";

    /** The shared secret presented in the X-API-Key header. */
    Optional<String> key();

    /** Email of the existing user the key acts as. */
    Optional<String> keyUserEmail();

    /** The key, if set to a non-blank value. */
    default Optional<String> configuredKey() {
        return key().filter(value -> !value.isBlank());
    }

    /** The user email, if set to a non-blank value. */
    default Optional<String> configuredUserEmail() {
        return keyUserEmail().filter(value -> !value.isBlank());
    }

    /** The feature authenticates anyone only when both values are set. */
    default boolean enabled() {
        return configuredKey().isPresent() && configuredUserEmail().isPresent();
    }

    /**
     * With only one of the two values set the feature silently stays disabled; returns a
     * warning naming the missing property — never the key itself.
     */
    default Optional<String> halfConfigurationWarning() {
        if (configuredKey().isPresent() == configuredUserEmail().isPresent()) {
            return Optional.empty();
        }
        String missing = configuredKey().isPresent() ? USER_EMAIL_PROPERTY : KEY_PROPERTY;
        return Optional.of(missing + " is not set - API-key access stays disabled until both "
                + KEY_PROPERTY + " and " + USER_EMAIL_PROPERTY + " are configured");
    }
}
