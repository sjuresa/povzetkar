package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.JobType;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.UserContextService;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP contract of {@code /api/v1/content-items} with the entities and services mocked (QA rule 3, POV-219).
 * Every scoped endpoint must look the item up through the caller's {@link ContentScope}, answer 404 for an
 * invisible item and leave foreign content untouched. The queries are tested in {@code ContentVisibilityQueriesTest}.
 */
@QuarkusTest
class ContentItemApiResourceTest {

    private static final String PATH = "/api/v1/content-items";
    private static final User VIEWER = UserBuilder.aUser().withId(7L).build();
    private static final ContentScope SCOPE = ContentScope.of(VIEWER);

    @InjectMock
    UserContextService userContextService;

    @InjectMock
    BackgroundJobService backgroundJobService;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(ContentItem.class);
        PanacheMock.mock(ContentSource.class);
        PanacheMock.mock(Summary.class);
        PanacheMock.mock(Topic.class);
        when(userContextService.getContentScope()).thenReturn(Optional.of(SCOPE));
        when(userContextService.getCurrentUser()).thenReturn(Optional.of(VIEWER));
    }

    // --- list ---

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void listReturnsTheVisibleItemsForTheCallersScopeAndFilters() {
        ContentItem item = item(11L, ProcessingStatus.PENDING);
        PanacheQuery<Long> ids = idQuery(List.of(11L));
        when(ContentItem.findVisibleIds(any(), any(), any())).thenReturn(ids);
        when(ContentItem.listByIdsWithAssociations(eq(List.of(11L)), any())).thenReturn(List.of(item));

        given().queryParam("status", "PENDING").when().get(PATH)
                .then().statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data.id", contains(11));

        PanacheMock.verify(ContentItem.class).findVisibleIds(eq(SCOPE),
                eq(new ContentItem.Filter(null, List.of(ProcessingStatus.PENDING), null)), any());
    }

    /** durationSeconds exists only on the YouTubeVideo subclass, not on the paged ContentItem root. */
    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void sortingBySubclassFieldIsRejected() {
        given().when().get(PATH + "?sort=durationSeconds,desc").then().statusCode(400);

        PanacheMock.verify(ContentItem.class, never()).findVisibleIds(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void filteringByAnInvisibleSourceIsNotFound() {
        when(ContentSource.findVisibleById(anyLong(), any())).thenReturn(Optional.empty());

        given().when().get(PATH + "?sourceId=5").then().statusCode(404);

        PanacheMock.verify(ContentSource.class).findVisibleById(5L, SCOPE);
        PanacheMock.verify(ContentItem.class, never()).findVisibleIds(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void filteringByAForeignTopicIsNotFound() {
        when(userContextService.findOwnedTopic(anyLong())).thenReturn(Optional.empty());

        given().when().get(PATH + "?topicId=8").then().statusCode(404);

        verify(userContextService).findOwnedTopic(8L);
        PanacheMock.verify(ContentItem.class, never()).findVisibleIds(any(), any(), any());
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "CONTENT_ITEM_VIEW" })
    void anAdminFiltersByAnyExistingTopic() {
        when(userContextService.getContentScope()).thenReturn(Optional.of(ContentScope.unrestricted()));
        Topic topic = TopicBuilder.aTopic().withId(8L).withUser(VIEWER).build();
        when(Topic.findByIdOptional(8L)).thenReturn(Optional.of(topic));
        PanacheQuery<Long> ids = idQuery(List.of());
        when(ContentItem.findVisibleIds(any(), any(), any())).thenReturn(ids);

        given().when().get(PATH + "?topicId=8").then().statusCode(200);

        PanacheMock.verify(ContentItem.class).findVisibleIds(eq(ContentScope.unrestricted()),
                eq(new ContentItem.Filter(null, List.of(), 8L)), any());
        verify(userContextService, never()).findOwnedTopic(anyLong());
    }

    // --- detail, transcript, summary ---

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void aVisibleItemIsReturnedWithTheViewersFirstTopicOnly() {
        ContentItem item = item(11L, ProcessingStatus.COMPLETED);
        User someoneElse = UserBuilder.aUser().withId(8L).build();
        // another user's topic on the same source must not leak, even with the lowest id
        item.contentSource.topics.add(TopicBuilder.aTopic().withId(0L).withName("Someone's Secret Topic").withUser(someoneElse).build());
        item.contentSource.topics.add(TopicBuilder.aTopic().withId(2L).withName("Beta Topic").withUser(VIEWER).build());
        item.contentSource.topics.add(TopicBuilder.aTopic().withId(1L).withName("Alpha Topic").withUser(VIEWER).build());
        when(ContentItem.findVisibleByIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(item));

        given().when().get(PATH + "/11")
                .then().statusCode(200)
                .body("data.id", equalTo(11))
                .body("data.topicName", equalTo("Alpha Topic")); // lowest id among the viewer's own topics
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void anInvisibleItemIsNotFoundOnEveryReadEndpoint() {
        when(ContentItem.findVisibleByIdWithAssociations(anyLong(), any())).thenReturn(Optional.empty());
        when(Summary.findVisibleByContentIdWithAssociations(anyLong(), any())).thenReturn(Optional.empty());

        given().when().get(PATH + "/12").then().statusCode(404);
        given().when().get(PATH + "/12/transcript").then().statusCode(404);
        given().when().get(PATH + "/12/summary").then().statusCode(404);

        PanacheMock.verify(ContentItem.class, times(2)).findVisibleByIdWithAssociations(12L, SCOPE);
        PanacheMock.verify(Summary.class).findVisibleByContentIdWithAssociations(12L, SCOPE);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void theTranscriptOfAVisibleVideoIsReturned() {
        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo().withId(13L).withContentSource(source()).build();
        video.transcript = "Spoken words of the video.";
        when(ContentItem.findVisibleByIdWithAssociations(13L, SCOPE)).thenReturn(Optional.of(video));

        given().when().get(PATH + "/13/transcript")
                .then().statusCode(200)
                .body("data.contentItemId", equalTo(13))
                .body("data.transcript", equalTo("Spoken words of the video."));
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void aVisibleItemWithoutTranscriptIsNotFound() {
        when(ContentItem.findVisibleByIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(item(11L, ProcessingStatus.PENDING)));

        given().when().get(PATH + "/11/transcript").then().statusCode(404);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void theSummaryOfAVisibleItemIsReturned() {
        Summary summary = summaryOf(item(11L, ProcessingStatus.COMPLETED));
        when(Summary.findVisibleByContentIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(summary));

        given().when().get(PATH + "/11/summary")
                .then().statusCode(200)
                .body("data.summaryText", containsString("Full text"))
                .body("data.executiveSummary", containsString("My Exec Summary"));
    }

    // --- share / unshare ---

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void sharingAVisibleSummaryReturnsItsPublicUuid() {
        Summary summary = summaryOf(item(11L, ProcessingStatus.COMPLETED));
        when(Summary.findVisibleByContentIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(summary));

        String uuid = given().contentType(ContentType.JSON).when().post(PATH + "/11/summary/share")
                .then().statusCode(200).extract().path("data");

        assertThat(uuid).isNotBlank().isEqualTo(summary.uuid);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void unsharingAVisibleSummaryRemovesTheUuid() {
        Summary summary = summaryOf(item(11L, ProcessingStatus.COMPLETED));
        summary.uuid = UUID.randomUUID().toString();
        when(Summary.findVisibleByContentIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(summary));

        given().contentType(ContentType.JSON).when().post(PATH + "/11/summary/unshare").then().statusCode(204);

        assertThat(summary.uuid).isNull();
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void aForeignSummaryIsNeitherSharedNorUnshared() {
        Summary unshared = summaryOf(item(20L, ProcessingStatus.COMPLETED));
        Summary shared = summaryOf(item(21L, ProcessingStatus.COMPLETED));
        shared.uuid = "someone-elses-public-link";
        // the unscoped lookup would find them — the resource must not use it
        when(Summary.findByContentIdWithAssociations(20L)).thenReturn(Optional.of(unshared));
        when(Summary.findByContentIdWithAssociations(21L)).thenReturn(Optional.of(shared));
        when(Summary.findVisibleByContentIdWithAssociations(anyLong(), any())).thenReturn(Optional.empty());

        given().contentType(ContentType.JSON).when().post(PATH + "/20/summary/share").then().statusCode(404);
        given().contentType(ContentType.JSON).when().post(PATH + "/21/summary/unshare").then().statusCode(404);

        assertThat(unshared.uuid).isNull();
        assertThat(shared.uuid).isEqualTo("someone-elses-public-link");
    }

    // --- generate-summary ---

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void generatingASummaryForAVisibleItemStartsAJob() {
        ContentItem item = item(11L, ProcessingStatus.FAILED);
        when(ContentItem.findVisibleByIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(item));
        when(backgroundJobService.triggerItemJob(any(), any(), any())).thenReturn(job());

        given().contentType(ContentType.JSON).when().post(PATH + "/11/commands/generate-summary")
                .then().statusCode(202)
                .header("Location", containsString("/api/v1/jobs/"));

        verify(backgroundJobService).triggerItemJob(item, JobType.GENERATE_ITEM_SUMMARY, VIEWER.id);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void generatingASummaryForACompletedItemIsAConflict() {
        when(ContentItem.findVisibleByIdWithAssociations(11L, SCOPE)).thenReturn(Optional.of(item(11L, ProcessingStatus.COMPLETED)));

        given().contentType(ContentType.JSON).when().post(PATH + "/11/commands/generate-summary").then().statusCode(409);

        verifyNoInteractions(backgroundJobService);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW", "SUMMARY_GENERATE" })
    void generatingASummaryForAForeignItemIsNotFoundAndStartsNoJob() {
        when(ContentItem.findByIdOptional(12L)).thenReturn(Optional.of(item(12L, ProcessingStatus.FAILED)));
        when(ContentItem.findVisibleByIdWithAssociations(anyLong(), any())).thenReturn(Optional.empty());

        given().contentType(ContentType.JSON).when().post(PATH + "/12/commands/generate-summary").then().statusCode(404);

        verifyNoInteractions(backgroundJobService);
    }

    // --- retry (admin) ---

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "ADMIN_JOB_UPDATE" })
    void retryingAnItemStartsAnAdminJob() {
        ContentItem item = item(11L, ProcessingStatus.FAILED);
        when(ContentItem.findByIdOptional(11L)).thenReturn(Optional.of(item));
        when(backgroundJobService.triggerItemJob(any(), any(), any())).thenReturn(job());

        given().contentType(ContentType.JSON).when().post(PATH + "/11/commands/retry")
                .then().statusCode(202)
                .header("Location", containsString("/api/v1/admin/jobs/"));

        verify(backgroundJobService).triggerItemJob(item, JobType.RETRY_ITEM_PROCESSING, VIEWER.id);
    }

    @Test
    @TestSecurity(user = "admin", roles = { "admin", "ADMIN_JOB_UPDATE" })
    void retryingACompletedItemIsAConflict() {
        when(ContentItem.findByIdOptional(11L)).thenReturn(Optional.of(item(11L, ProcessingStatus.COMPLETED)));

        given().contentType(ContentType.JSON).when().post(PATH + "/11/commands/retry").then().statusCode(409);

        verifyNoInteractions(backgroundJobService);
    }

    @Test
    @TestSecurity(user = "someone", roles = { "user", "CONTENT_ITEM_VIEW" })
    void usersCannotRetryItems() {
        given().contentType(ContentType.JSON).when().post(PATH + "/11/commands/retry").then().statusCode(403);
    }

    private static ContentSource source() {
        return ContentSourceBuilder.aContentSource().withId(3L).build();
    }

    private static ContentItem item(Long id, ProcessingStatus status) {
        ContentItem item = ContentItemBuilder.aContentItem().withContentSource(source()).withTitle("Item " + id)
                .withProcessingStatus(status).build();
        item.id = id;
        return item;
    }

    private static Summary summaryOf(ContentItem item) {
        return SummaryBuilder.aSummary().withId(item.id + 100).withContentItem(item)
                .withSummaryText("### Executive Summary\nMy Exec Summary\n###\nFull text").build();
    }

    private static BackgroundJob job() {
        BackgroundJob job = new BackgroundJob();
        job.id = UUID.randomUUID();
        return job;
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
