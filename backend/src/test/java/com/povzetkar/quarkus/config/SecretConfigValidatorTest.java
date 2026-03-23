package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SecretConfigValidator}.
 * <p>
 * Verifies that:
 * 1. The validator bean starts successfully (proving all secrets are present in test env).
 * 2. The required secrets list is consistent and non-empty.
 * 3. Sensitive config values are NOT exposed through config log output
 *    (enforced by quarkus.config.log.values=false in application.properties).
 */
@QuarkusTest
class SecretConfigValidatorTest {

    @Inject
    Config config;

    @Inject
    SecretConfigValidator validator;

    @Test
    void validatorBeanShouldBeInjectable() {
        assertThat(validator).isNotNull();
    }

    @Test
    void requiredSecretsListShouldNotBeEmpty() {
        assertThat(SecretConfigValidator.REQUIRED_SECRETS)
                .isNotEmpty()
                .contains("youtube.api.key", "google.api.keys", "quarkus.datasource.password");
    }

    @Test
    void configLogValuesShouldBeDisabled() {
        var logValues = config.getOptionalValue("quarkus.config.log.values", Boolean.class);
        assertThat(logValues)
                .isPresent()
                .hasValue(false);
    }

    @Test
    void secretKeysShouldBeMasked() {
        var secretKeys = config.getOptionalValue("quarkus.config.secret-keys", String.class);
        assertThat(secretKeys).isPresent();
        String keys = secretKeys.get();
        assertThat(keys).contains("youtube.api.key");
        assertThat(keys).contains("google.api.keys");
        assertThat(keys).contains("quarkus.datasource.password");
        assertThat(keys).contains("quarkus.oidc.credentials.secret");
    }

    @Test
    void allRequiredSecretsShouldBeConfiguredInTestProfile() {
        for (String key : SecretConfigValidator.REQUIRED_SECRETS) {
            var value = config.getOptionalValue(key, String.class);
            assertThat(value)
                    .as("Secret key '%s' should be configured in test profile", key)
                    .isPresent();
            assertThat(value.get())
                    .as("Secret key '%s' should not be blank", key)
                    .isNotBlank();
        }
    }
}
