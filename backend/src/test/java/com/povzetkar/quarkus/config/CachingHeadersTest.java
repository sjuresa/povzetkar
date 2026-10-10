package com.povzetkar.quarkus.config;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Browser caching policy (POV-214). Quarkus serves static files with {@code public, immutable,
 * max-age=86400} by default, so after a deploy a browser kept the old SPA shell, locale files and legacy
 * scripts for up to a day. Only the content-hashed {@code /assets/*} may be cached as immutable;
 * everything else is revalidated. API responses and the legacy pages keep their own headers.
 */
@QuarkusTest
class CachingHeadersTest {

    /** Stand-in for a Vite build output file: src/test/resources/META-INF/resources/assets/test-asset.js. */
    private static final String HASHED_ASSET = "/assets/test-asset.js";

    @Test
    void hashedAssetsAreImmutableForAYear() {
        // Written by Vert.x's StaticHandler (quarkus.http.static-resources.max-age) — only for a served file
        given().when().get(HASHED_ASSET)
                .then().statusCode(200)
                .header("Cache-Control", "public, immutable, max-age=31536000");
    }

    @Test
    void missingAssetIsNotCachedAsImmutable() {
        // A 404 for a hashed URL (e.g. a request racing a deploy) must not be pinned in browsers or at Cloudflare
        assertThat(given().when().get("/assets/missing-asset.js").then().statusCode(404).extract()
                .headers().getValues("Cache-Control"))
                .noneMatch(value -> value.contains("immutable") || value.contains("max-age"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/static/css/app.css", "/static/js/app.js"})
    void unhashedStaticFilesAreRevalidated(String path) {
        given().when().get(path)
                .then().statusCode(200)
                .header("Cache-Control", "no-cache");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/index.html", "/dashboard", "/locales/sl.json", "/favicon.svg"})
    void spaShellAndPublicFilesAreRevalidated(String path) {
        // The shell comes from the test index.html stand-in; the locale and favicon files are not on the test
        // classpath (404 here), and the header must still be set
        assertThat(given().redirects().follow(false).when().get(path).getHeaders().getValues("Cache-Control"))
                .as(path).containsExactly("no-cache");
    }

    @Test
    void revalidationAnswers304WhenUnchanged() {
        String lastModified = given().when().get("/static/css/app.css")
                .then().statusCode(200).extract().header("Last-Modified");
        assertThat(lastModified).isNotBlank();

        given().header("If-Modified-Since", lastModified)
                .when().get("/static/css/app.css")
                .then().statusCode(304);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/login", "/logout", "/ui/admin"})
    void legacyPagesAreLeftToTheirOwnHandlers(String path) {
        // LoginResource/ViewUtils set "no-cache, no-store, must-revalidate" where they need it; the caching
        // filters must neither add a value nor merge one into theirs
        assertThat(given().redirects().follow(false).when().get(path).getHeaders().getValues("Cache-Control"))
                .as(path).doesNotContain("no-cache", "public, immutable, max-age=31536000");
    }

    @Test
    void apiResponsesGetNoCachingFilterHeader() {
        assertThat(given().when().get("/api/v1/system/metadata").getHeaders().getValues("Cache-Control"))
                .doesNotContain("no-cache", "public, immutable, max-age=31536000");
    }
}
