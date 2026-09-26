package com.povzetkar.quarkus.security;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ApiKeyConfig} is the single place that decides whether API-key access is configured.
 * A half-configured key (only one of key / user email) silently disabled the feature; it is now
 * reported at startup without ever revealing the key (POV-208).
 */
class ApiKeyConfigTest {

    private static final String KEY = "secret-key-value-4f2a";
    private static final String EMAIL = "agent@example.com";

    private record Values(Optional<String> key, Optional<String> keyUserEmail) implements ApiKeyConfig {
    }

    private static ApiKeyConfig config(String key, String email) {
        return new Values(Optional.ofNullable(key), Optional.ofNullable(email));
    }

    @Test
    void keyWithoutEmailIsDisabledAndNamesTheMissingEmailPropertyWithoutTheKey() {
        ApiKeyConfig config = config(KEY, null);

        assertThat(config.enabled()).isFalse();
        assertThat(config.halfConfigurationWarning()).hasValueSatisfying(warning -> assertThat(warning)
                .startsWith("povzetkar.api.key-user-email is not set")
                .doesNotContain(KEY));
    }

    @Test
    void emailWithBlankKeyIsDisabledAndNamesTheMissingKeyProperty() {
        ApiKeyConfig config = config(" ", EMAIL);

        assertThat(config.enabled()).isFalse();
        assertThat(config.halfConfigurationWarning()).hasValueSatisfying(warning -> assertThat(warning)
                .startsWith("povzetkar.api.key is not set"));
    }

    @Test
    void bothSetIsEnabledWithoutWarning() {
        ApiKeyConfig config = config(KEY, EMAIL);

        assertThat(config.enabled()).isTrue();
        assertThat(config.configuredKey()).hasValue(KEY);
        assertThat(config.configuredUserEmail()).hasValue(EMAIL);
        assertThat(config.halfConfigurationWarning()).isEmpty();
    }

    @Test
    void noneSetIsDisabledWithoutWarning() {
        ApiKeyConfig config = config("", null);

        assertThat(config.enabled()).isFalse();
        assertThat(config.configuredKey()).isEmpty();
        assertThat(config.halfConfigurationWarning()).isEmpty();
    }
}
