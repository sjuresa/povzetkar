package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** HTTP contract of {@code GET /api/v1/sources/{id}/items} with the entities mocked (QA rule 3, POV-219). */
@QuarkusTest
class SourceItemApiResourceTest {

    private static final ContentScope SCOPE = ContentScope.of(UserBuilder.aUser().withId(7L).build());

    @InjectMock
    UserContextService userContextService;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(ContentSource.class);
        PanacheMock.mock(ContentItem.class);
        PanacheMock.mock(Summary.class);
        when(userContextService.getContentScope()).thenReturn(Optional.of(SCOPE));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void itemsOfAVisibleSourceAreReturned() {
        ContentSource source = ContentSourceBuilder.aContentSource().withId(3L).withName("AI Channel").build();
        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo().withId(11L).withTitle("AI Breakthrough")
                .withContentSource(source).withDuration(Duration.ofMinutes(5)).withVideoId("vid123").build();
        when(ContentSource.findVisibleById(3L, SCOPE)).thenReturn(Optional.of(source));
        PanacheQuery<Long> ids = idQuery(List.of(11L));
        when(ContentItem.findIdsBySource(eq(source), any())).thenReturn(ids);
        when(ContentItem.listByIdsWithAssociations(eq(List.of(11L)), any())).thenReturn(List.of(video));
        when(Summary.findContentItemIdsWithSummary(List.of(11L))).thenReturn(Set.of(11L));

        given().when().get("/api/v1/sources/3/items")
                .then().statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].title", equalTo("AI Breakthrough"))
                .body("data[0].durationSeconds", is(300))
                .body("data[0].thumbnailUrl", containsString("vid123"))
                .body("data[0].hasSummary", is(true));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void anInvisibleSourceIsNotFoundAndItsItemsAreNotRead() {
        when(ContentSource.findVisibleById(anyLong(), any())).thenReturn(Optional.empty());

        given().when().get("/api/v1/sources/4/items").then().statusCode(404);

        PanacheMock.verify(ContentSource.class).findVisibleById(4L, SCOPE);
        PanacheMock.verify(ContentItem.class, never()).findIdsBySource(any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "SOURCE_VIEW" })
    void anEmptyPageLoadsNothingElse() {
        ContentSource source = ContentSourceBuilder.aContentSource().withId(3L).build();
        when(ContentSource.findVisibleById(3L, SCOPE)).thenReturn(Optional.of(source));
        PanacheQuery<Long> ids = idQuery(List.of());
        when(ContentItem.findIdsBySource(eq(source), any())).thenReturn(ids);

        given().when().get("/api/v1/sources/3/items")
                .then().statusCode(200)
                .body("data", hasSize(0));

        PanacheMock.verify(ContentItem.class, never()).listByIdsWithAssociations(any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user" })
    void withoutSourceViewItIsForbidden() {
        given().when().get("/api/v1/sources/3/items").then().statusCode(403);
    }

    @SuppressWarnings("unchecked")
    private static PanacheQuery<Long> idQuery(List<Long> ids) {
        PanacheQuery<Long> query = mock(PanacheQuery.class);
        when(query.count()).thenReturn((long) ids.size());
        when(query.page(anyInt(), anyInt())).thenReturn(query);
        when(query.list()).thenReturn(ids);
        return query;
    }
}
