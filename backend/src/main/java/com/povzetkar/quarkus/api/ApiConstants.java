package com.povzetkar.quarkus.api;

/**
 * Shared constants for the API layer.
 */
public final class ApiConstants {

    private ApiConstants() {
        // Private constructor to prevent instantiation
    }

    /**
     * Maximum allowed page size for paginated responses.
     */
    public static final int MAX_PAGE_SIZE = 100;

    /**
     * Default page size for paginated responses.
     */
    public static final int DEFAULT_PAGE_SIZE = 20;
}
