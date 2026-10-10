package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.service.ContentSourceDetailService;
import com.povzetkar.quarkus.service.ContentSourceService;
import com.povzetkar.quarkus.service.SourceDetailsUnavailableException;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import jakarta.ws.rs.core.Response;
import org.hibernate.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP contract and security of {@code /api/v1/sources}, with {@link ContentSource} and the services mocked
 * (QA rule 3). The visibility queries themselves are tested against the database in
 * {@code ContentVisibilityQueriesTest}; here the resource must pass the caller's {@link ContentScope} and turn an
 * invisible source into 404 (POV-219).
 */
@QuarkusTest
class ContentSourceResourceTest {

    private static final String PATH = "/api/v1/sources";
    private static final ContentScope SCOPE = ContentScope.of(UserBuilder.aUser().withId(7L).build());

    @InjectMock
    UserContextService userContextService;

    @InjectMock
    ContentSourceService contentSourceService;

    @InjectMock
    Session session;

    @InjectMock
    ContentSourceDetailService contentSourceDetailService;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(ContentSource.class);
        when(userContextService.getContentScope()).thenReturn(Optional.of(SCOPE));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void listReturnsTheVisibleSourcesForTheCallersScope() {
        PanacheQuery<ContentSource> query = queryOf(source(1L, "Alpha"), source(2L, "Beta"));
        when(ContentSource.findVisible(any(), any(), any())).thenReturn(query);

        given().when().get(PATH)
                .then().statusCode(200)
                .body("meta.pagination.totalElements", equalTo(2))
                .body("data.name", contains("Alpha", "Beta"));

        PanacheMock.verify(ContentSource.class).findVisible(SCOPE, "", null);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void listPassesSearchAndExcludedIds() {
        PanacheQuery<ContentSource> query = queryOf();
        when(ContentSource.findVisible(any(), any(), any())).thenReturn(query);

        given().queryParam("search", "ai").queryParam("excludeIds", "3, 4")
                .when().get(PATH)
                .then().statusCode(200)
                .body("meta.pagination.totalElements", equalTo(0))
                .body("data", hasSize(0));

        PanacheMock.verify(ContentSource.class).findVisible(SCOPE, "ai", List.of(3L, 4L));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void nonNumericExcludedIdsAreABadRequest() {
        given().queryParam("excludeIds", "3,x").when().get(PATH).then().statusCode(400);

        PanacheMock.verify(ContentSource.class, never()).findVisible(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void callerWithoutContentScopeIsUnauthorized() {
        when(userContextService.getContentScope()).thenReturn(Optional.empty());

        given().when().get(PATH).then().statusCode(401);

        PanacheMock.verify(ContentSource.class, never()).findVisible(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void visibleSourceIsReturned() {
        when(ContentSource.findVisibleById(5L, SCOPE)).thenReturn(Optional.of(source(5L, "Mine")));

        given().when().get(PATH + "/5").then().statusCode(200).body("data.name", equalTo("Mine"));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void invisibleOrUnknownSourceIsNotFound() {
        when(ContentSource.findVisibleById(anyLong(), any())).thenReturn(Optional.empty());

        given().when().get(PATH + "/6").then().statusCode(404);

        PanacheMock.verify(ContentSource.class).findVisibleById(6L, SCOPE);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_CREATE" })
    void createReturns201ForANewSourceAnd200ForAnExistingOne() {
        ContentSourceCreateDTO dto = new ContentSourceCreateDTO("YOUTUBE", "@channel", "Channel");
        when(contentSourceService.findOrCreate(dto))
                .thenReturn(new ContentSourceService.Result(source(9L, "Channel"), true))
                .thenReturn(new ContentSourceService.Result(source(9L, "Channel"), false));

        given().contentType(ContentType.JSON).body(dto).when().post(PATH)
                .then().statusCode(201)
                .header("Location", org.hamcrest.Matchers.endsWith(PATH + "/9"))
                .body("data.name", equalTo("Channel"));
        given().contentType(ContentType.JSON).body(dto).when().post(PATH)
                .then().statusCode(200)
                .header("Location", org.hamcrest.Matchers.endsWith(PATH + "/9"));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void usersCannotCreateSourcesDirectly() {
        given().contentType(ContentType.JSON).body(new ContentSourceCreateDTO("YOUTUBE", "@channel", null))
                .when().post(PATH)
                .then().statusCode(403);

        verifyNoInteractions(contentSourceService);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_CREATE" })
    void createRejectsWhatTheServiceRejects() {
        when(contentSourceService.findOrCreate(any())).thenThrow(
                new BusinessRuleException(Response.Status.BAD_REQUEST, "PLATFORM_NOT_SUPPORTED", "Platform BLOG is not supported yet"));

        given().contentType(ContentType.JSON).body(new ContentSourceCreateDTO("BLOG", "https://blog.example", null))
                .when().post(PATH)
                .then().statusCode(400)
                .body("error.errorCode", equalTo("PLATFORM_NOT_SUPPORTED"));
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_CREATE" })
    void createValidatesTheBody() {
        given().contentType(ContentType.JSON).body(Map.of("platform", "YOUTUBE", "name", "No identifier"))
                .when().post(PATH).then().statusCode(400);
        given().contentType(ContentType.JSON).body("{}")
                .when().post(PATH).then().statusCode(400);

        verifyNoInteractions(contentSourceService);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_DELETE" })
    void deleteNeedsIfMatch() {
        when(ContentSource.findByIdOptional(5L)).thenReturn(Optional.of(source(5L, "Doomed")));

        given().when().delete(PATH + "/5").then().statusCode(428);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_DELETE" })
    void deleteWithAStaleVersionIsAConflict() {
        when(ContentSource.findByIdOptional(5L)).thenReturn(Optional.of(source(5L, "Doomed")));

        given().header("If-Match", "\"999\"").when().delete(PATH + "/5").then().statusCode(409);

        verify(session, never()).remove(any());
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_DELETE" })
    void deleteRemovesTheSource() {
        ContentSource doomed = source(5L, "Doomed");
        when(ContentSource.findByIdOptional(5L)).thenReturn(Optional.of(doomed));
        when(session.contains(doomed)).thenReturn(true);

        given().header("If-Match", "\"" + doomed.version + "\"").when().delete(PATH + "/5").then().statusCode(204);

        verify(session).remove(doomed);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "SOURCE_DELETE" })
    void deletingAnUnknownSourceIsNotFound() {
        when(ContentSource.findByIdOptional(anyLong())).thenReturn(Optional.empty());

        given().header("If-Match", "\"0\"").when().delete(PATH + "/99").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void usersCannotDeleteSources() {
        given().header("If-Match", "\"0\"").when().delete(PATH + "/5").then().statusCode(403);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void theChannelCheckTellsNotFoundFromUnavailable() {
        when(contentSourceDetailService.fetchContentSourceDetails(1, "@found")).thenReturn(Optional.of(
                new ContentSourceDetailService.ContentSourceDetails("UC1", "Found", "", 1)));
        when(contentSourceDetailService.fetchContentSourceDetails(1, "@nobody")).thenReturn(Optional.empty());
        when(contentSourceDetailService.fetchContentSourceDetails(1, "@busy"))
                .thenThrow(new SourceDetailsUnavailableException("quota", null));

        given().when().get(PATH + "/details/1/@found").then().statusCode(200).body("data.channelId", equalTo("UC1"));
        given().when().get(PATH + "/details/1/@nobody").then().statusCode(404);
        given().when().get(PATH + "/details/1/@busy").then().statusCode(503)
                .body("error.errorCode", equalTo("SOURCE_CHECK_UNAVAILABLE"));
    }

    /** Quick Add sends a channel URL percent-encoded as one path segment (PR #286 review). */
    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void theChannelCheckAcceptsAnEncodedUrl() {
        String url = "https://www.youtube.com/@veritasium";
        when(contentSourceDetailService.fetchContentSourceDetails(1, url)).thenReturn(Optional.of(
                new ContentSourceDetailService.ContentSourceDetails("UC1", "Veritasium", "", 1)));

        given().urlEncodingEnabled(false)
                .when().get(PATH + "/details/1/" + java.net.URLEncoder.encode(url, java.nio.charset.StandardCharsets.UTF_8))
                .then().statusCode(200)
                .body("data.channelId", equalTo("UC1"));
    }

    private static ContentSource source(Long id, String name) {
        return ContentSourceBuilder.aContentSource().withId(id).withName(name).build();
    }

    @SuppressWarnings("unchecked")
    private static PanacheQuery<ContentSource> queryOf(ContentSource... sources) {
        PanacheQuery<ContentSource> query = mock(PanacheQuery.class);
        when(query.count()).thenReturn((long) sources.length);
        when(query.page(anyInt(), anyInt())).thenReturn(query);
        when(query.stream()).thenAnswer(invocation -> Stream.of(sources));
        return query;
    }
}
