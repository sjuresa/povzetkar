package com.povzetkar.quarkus.config;

import com.povzetkar.quarkus.ui.util.SpaRoutingHandler;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.config.MeterFilter;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Set;

/**
 * Bounds the {@code uri} tag of the HTTP server metrics (POV-221). Quarkus tags a request without a JAX-RS
 * path template with its literal path, so every SPA deep link (/sources/42, /s/&lt;uuid&gt;) and static file
 * (/assets/index-&lt;hash&gt;.js) created its own series until Micrometer's limit of 100 {@code uri} values
 * stopped recording (POV-210). Kept: Quarkus' special values (NOT_FOUND, REDIRECTION, UNKNOWN, root), canonical
 * values under /api/ and /ui/ (JAX-RS templates and the legacy UI's Vert.x routes; an untemplated 4xx there is
 * already UNKNOWN through quarkus.micrometer.binder.http-server.suppress4xx-errors) and the fixed login routes
 * (/q/ is not metered). Everything else becomes SPA, STATIC or UNKNOWN — an allow-list, because the tag carries the
 * raw request path while routing uses the normalised one: /%73ources/1, /sources/1/%2e%2e/2 and
 * /api/%2e%2e/sources/1 are all served as /sources/1, so classifying by pattern alone left endless raw variants.
 */
@Singleton
public class HttpMetricsConfig {

    static final String URI_TAG = "uri";
    static final String SPA = "SPA";
    static final String STATIC = "STATIC";
    static final String UNKNOWN = "UNKNOWN";

    private static final List<String> TEMPLATED_PREFIXES = List.of("/api/", "/ui/");
    private static final Set<String> FIXED_ROUTES = Set.of("/login", "/logout", "/auth/login");
    /** Raw-path features that normalisation changes; templates and route paths never contain them. */
    private static final List<String> NON_CANONICAL_MARKERS = List.of("%", "/.", "//", ";", "\\");

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

    /**
     * Special values, canonical values under the templated prefixes and the fixed routes are kept; SPA routes
     * become {@value #SPA}, files {@value #STATIC}; any other path {@value #UNKNOWN}.
     */
    static String collapse(String uri) {
        boolean specialValue = !uri.startsWith("/");
        if (specialValue || FIXED_ROUTES.contains(uri)) {
            return uri;
        }
        boolean canonical = NON_CANONICAL_MARKERS.stream().noneMatch(uri::contains);
        if (canonical && TEMPLATED_PREFIXES.stream().anyMatch(uri::startsWith)) {
            return uri;
        }
        if (SpaRoutingHandler.isSpaRoute(uri)) {
            return SPA;
        }
        if (SpaRoutingHandler.isFile(uri)) {
            return STATIC;
        }
        return UNKNOWN;
    }
}
