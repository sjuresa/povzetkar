package com.povzetkar.quarkus.config;

import com.povzetkar.quarkus.ui.util.SpaRoutingHandler;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Bounds the {@code uri} tag of the HTTP server metrics (POV-221). Quarkus tags a request without a JAX-RS
 * path template with its literal path, so every SPA deep link (/sources/42, /s/&lt;uuid&gt;) and static file
 * (/assets/index-&lt;hash&gt;.js) created its own series until Micrometer's limit of 100 {@code uri} values
 * stopped recording (POV-210). API templates and Quarkus' special values (NOT_FOUND, REDIRECTION, UNKNOWN,
 * root) are kept; untemplated 4xx become UNKNOWN through quarkus.micrometer.binder.http-server.suppress4xx-errors.
 */
@Singleton
public class HttpMetricsConfig {

    static final String URI_TAG = "uri";
    static final String SPA = "SPA";
    static final String STATIC = "STATIC";

    private static final MeterFilter COLLAPSE_URI = MeterFilter.replaceTagValues(URI_TAG, HttpMetricsConfig::collapse);

    @Produces
    @Singleton
    public MeterFilter boundedHttpServerUri() {
        return new MeterFilter() {
            @Override
            public Meter.Id map(Meter.Id id) {
                return id.getName().startsWith("http.server") ? COLLAPSE_URI.map(id) : id;
            }
        };
    }

    /** SPA routes become {@value #SPA}, files outside /api become {@value #STATIC}; anything else is kept. */
    static String collapse(String uri) {
        if (SpaRoutingHandler.isSpaRoute(uri)) {
            return SPA;
        }
        if (!uri.startsWith("/api/") && uri.lastIndexOf('.') > uri.lastIndexOf('/')) {
            return STATIC;
        }
        return uri;
    }
}
