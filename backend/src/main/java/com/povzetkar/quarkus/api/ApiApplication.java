package com.povzetkar.quarkus.api;

import jakarta.ws.rs.core.Application;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeIn;
import org.eclipse.microprofile.openapi.annotations.enums.SecuritySchemeType;
import org.eclipse.microprofile.openapi.annotations.security.SecurityScheme;

/**
 * JAX-RS application descriptor carrying API-wide OpenAPI metadata.
 */
@SecurityScheme(
        securitySchemeName = "apiKey",
        type = SecuritySchemeType.APIKEY,
        apiKeyName = ApiApplication.API_KEY_HEADER,
        in = SecuritySchemeIn.HEADER,
        description = "Read-only API key for automation agents (POV-205). Grants the four VIEW permissions of the mapped user; non-GET requests are rejected.")
public class ApiApplication extends Application {

    static final String API_KEY_HEADER = "X-API-Key";
}
