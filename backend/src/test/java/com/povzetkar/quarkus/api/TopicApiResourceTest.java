package com.povzetkar.quarkus.api;

import com.povzetkar.quarkus.api.dto.ContentSourceCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicCreateDTO;
import com.povzetkar.quarkus.api.dto.TopicPatchDTO;
import com.povzetkar.quarkus.api.exception.BusinessRuleException;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.BackgroundJob;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.service.BackgroundJobService;
import com.povzetkar.quarkus.service.ContentSourceService;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * HTTP contract of {@code /api/v1/topics} with the entities and services mocked (QA rule 3). Since POV-219 a
 * topic accepts only sources visible to the caller ({@code sourceIds}) and sources entered by identifier
 * ({@code newSources}), and nothing on the topic changes before every source has been validated.
 */
@QuarkusTest
class TopicApiResourceTest {

    private static final String PATH = "/api/v1/topics";
    private static final String ALL_TOPIC_ROLES_USER = "someone";
    private static final User OWNER = UserBuilder.aUser().withId(7L).withUsername("owner").build();
    private static final ContentScope SCOPE = ContentScope.of(OWNER);

    @InjectMock
    UserContextService userContextService;

    @InjectMock
    ContentSourceService contentSourceService;

    @InjectMock
    BackgroundJobService backgroundJobService;

    @InjectMock
    Session session;

    @BeforeEach
    void setUp() {
        PanacheMock.mock(Topic.class);
        PanacheMock.mock(ContentSource.class);
        when(userContextService.getCurrentUser()).thenReturn(Optional.of(OWNER));
        when(userContextService.getContentViewingUser()).thenReturn(Optional.of(OWNER));
        when(userContextService.getContentScope()).thenReturn(Optional.of(SCOPE));
        when(Topic.findByUserAndName(any(), any())).thenReturn(Optional.empty());
        when(ContentSource.listVisibleByIds(any(), any())).thenReturn(List.of());
        doAnswer(invocation -> {
            if (invocation.getArgument(0) instanceof Topic topic) {
                topic.id = 42L;
            }
            return null;
        }).when(session).persist(any());
    }

    // --- read ---

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_VIEW" })
    void listReturnsTheViewersTopics() {
        Topic topic = topic(1L, "Initial Setup Topic");
        PanacheQuery<Topic> query = topicQuery(topic);
        when(Topic.findByUserWithSources(any(), any())).thenReturn(query);

        given().when().get(PATH)
                .then().statusCode(200)
                .body("meta.pagination.totalElements", equalTo(1))
                .body("data[0].name", equalTo("Initial Setup Topic"))
                .body("data[0].enabled", equalTo(true));

        PanacheMock.verify(Topic.class).findByUserWithSources(eq(OWNER), any());
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_VIEW" })
    void anOwnTopicIsReturnedAndAForeignOneIsNotFound() {
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic(1L, "Mine")));
        when(Topic.findByIdAndUserWithSources(2L, OWNER)).thenReturn(Optional.empty());

        given().when().get(PATH + "/1").then().statusCode(200).body("data.name", equalTo("Mine"));
        given().when().get(PATH + "/2").then().statusCode(404);
    }

    // --- create ---

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void createAttachesVisibleSourcesAndQuickAddSources() {
        ContentSource existing = source(1L, "UC-existing");
        ContentSource created = source(2L, "UC-created");
        ContentSourceCreateDTO quickAdd = new ContentSourceCreateDTO("YOUTUBE", "@created", "Created");
        when(ContentSource.listVisibleByIds(Set.of(1L), SCOPE)).thenReturn(List.of(existing));
        when(contentSourceService.findOrCreate(quickAdd)).thenReturn(new ContentSourceService.Result(created, true));

        given().contentType(ContentType.JSON)
                .body(new TopicCreateDTO("New Topic", true, "Summarize this", Set.of(1L), Set.of(quickAdd)))
                .when().post(PATH)
                .then().statusCode(201)
                .header("Location", containsString(PATH + "/42"))
                .body("data.name", equalTo("New Topic"))
                .body("data.promptTemplate", equalTo("Summarize this"))
                .body("data.contentSources.id", containsInAnyOrder(1, 2));
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void createWithAForeignSourceIsNotFoundAndCreatesNothing() {
        when(ContentSource.listVisibleByIds(any(), any())).thenReturn(List.of());

        given().contentType(ContentType.JSON)
                .body(new TopicCreateDTO("New Topic", true, null, Set.of(9L), Set.of(new ContentSourceCreateDTO("YOUTUBE", "@x", null))))
                .when().post(PATH)
                .then().statusCode(404);

        PanacheMock.verify(ContentSource.class).listVisibleByIds(Set.of(9L), SCOPE);
        verifyNoInteractions(contentSourceService);
        verify(session, never()).persist(org.mockito.ArgumentMatchers.any(Topic.class));
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void quickAddEntriesAreFoundOrCreatedOncePerIdentifier() {
        ContentSourceCreateDTO first = new ContentSourceCreateDTO("YOUTUBE", "@same", "Same");
        ContentSourceCreateDTO padded = new ContentSourceCreateDTO("YOUTUBE", "  @same ", "Same again");
        when(contentSourceService.findOrCreate(any())).thenReturn(new ContentSourceService.Result(source(2L, "UC-same"), false));

        given().contentType(ContentType.JSON)
                .body(new TopicCreateDTO("New Topic", true, null, null, Set.of(first, padded)))
                .when().post(PATH)
                .then().statusCode(201)
                .body("data.contentSources", hasSize(1));

        verify(contentSourceService, times(1)).findOrCreate(any());
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void aRejectedQuickAddSourceFailsTheWholeCreate() {
        when(contentSourceService.findOrCreate(any())).thenThrow(
                new BusinessRuleException(Response.Status.BAD_REQUEST, "PLATFORM_NOT_SUPPORTED", "Platform BLOG is not supported yet"));

        given().contentType(ContentType.JSON)
                .body(new TopicCreateDTO("New Topic", true, null, null, Set.of(new ContentSourceCreateDTO("BLOG", "https://blog.example", null))))
                .when().post(PATH)
                .then().statusCode(400)
                .body("error.errorCode", equalTo("PLATFORM_NOT_SUPPORTED"));

        verify(session, never()).persist(org.mockito.ArgumentMatchers.any(Topic.class));
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void aDuplicateTopicNameIsAConflictBeforeAnySourceIsTouched() {
        when(Topic.findByUserAndName(OWNER, "Taken")).thenReturn(Optional.of(topic(1L, "Taken")));

        given().contentType(ContentType.JSON)
                .body(new TopicCreateDTO("Taken", true, null, Set.of(1L), Set.of(new ContentSourceCreateDTO("YOUTUBE", "@x", null))))
                .when().post(PATH)
                .then().statusCode(409);

        PanacheMock.verify(ContentSource.class, never()).listVisibleByIds(any(), any());
        verifyNoInteractions(contentSourceService);
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_CREATE" })
    void aNullQuickAddEntryIsABadRequest() {
        given().contentType(ContentType.JSON).body("{\"name\":\"New Topic\",\"newSources\":[null]}")
                .when().post(PATH)
                .then().statusCode(400);

        verifyNoInteractions(contentSourceService);
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_VIEW" })
    void createNeedsTopicCreate() {
        given().contentType(ContentType.JSON).body(new TopicCreateDTO("Forbidden Topic", true, null, null, null))
                .when().post(PATH)
                .then().statusCode(403);
    }

    // --- patch ---

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_UPDATE" })
    void patchUpdatesTheFields() {
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic(1L, "Initial Setup Topic")));

        given().header("If-Match", "\"0\"").contentType(ContentType.JSON)
                .body(new TopicPatchDTO(null, false, "Updated prompt", null, null, 0))
                .when().patch(PATH + "/1")
                .then().statusCode(200)
                .body("data.name", equalTo("Initial Setup Topic"))
                .body("data.promptTemplate", equalTo("Updated prompt"))
                .body("data.enabled", equalTo(false));
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_UPDATE" })
    void patchWithAForeignSourceIsNotFoundAndLeavesTheTopicUntouched() {
        Topic topic = topic(1L, "Initial Setup Topic");
        ContentSource attached = source(1L, "UC-attached");
        topic.contentSources.add(attached);
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic));
        when(ContentSource.listVisibleByIds(any(), any())).thenReturn(List.of(attached));

        given().header("If-Match", "\"0\"").contentType(ContentType.JSON)
                .body(new TopicPatchDTO("Renamed", false, null, Set.of(1L, 9L), null, 0))
                .when().patch(PATH + "/1")
                .then().statusCode(404);

        assertThat(topic.contentSources).containsExactly(attached);
        assertThat(topic.name).isEqualTo("Initial Setup Topic");
        assertThat(topic.enabled).isTrue();
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_UPDATE" })
    void patchWithOnlyQuickAddSourcesKeepsTheExistingOnes() {
        Topic topic = topic(1L, "Initial Setup Topic");
        topic.contentSources.add(source(1L, "UC-attached"));
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic));
        when(contentSourceService.findOrCreate(any())).thenReturn(new ContentSourceService.Result(source(2L, "UC-new"), true));

        given().header("If-Match", "\"0\"").contentType(ContentType.JSON)
                .body(new TopicPatchDTO(null, null, null, null, Set.of(new ContentSourceCreateDTO("YOUTUBE", "@new", null)), 0))
                .when().patch(PATH + "/1")
                .then().statusCode(200)
                .body("data.contentSources.id", containsInAnyOrder(1, 2));

        PanacheMock.verify(ContentSource.class, never()).listVisibleByIds(any(), any());
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_UPDATE" })
    void patchReplacesTheSourcesAndDoesNotDuplicateAResolvedQuickAddSource() {
        Topic topic = topic(1L, "Initial Setup Topic");
        topic.contentSources.add(source(1L, "UC-dropped"));
        ContentSource kept = source(3L, "UC-kept");
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic));
        when(ContentSource.listVisibleByIds(Set.of(3L), SCOPE)).thenReturn(List.of(kept));
        // Quick Add entered the same channel by handle: it resolves to the source already listed
        when(contentSourceService.findOrCreate(any())).thenReturn(new ContentSourceService.Result(source(3L, "UC-kept"), false));

        given().header("If-Match", "\"0\"").contentType(ContentType.JSON)
                .body(new TopicPatchDTO(null, null, null, Set.of(3L), Set.of(new ContentSourceCreateDTO("YOUTUBE", "@kept", null)), 0))
                .when().patch(PATH + "/1")
                .then().statusCode(200)
                .body("data.contentSources.id", contains(3));
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_UPDATE" })
    void patchNeedsAMatchingIfMatch() {
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic(1L, "Initial Setup Topic")));

        given().contentType(ContentType.JSON).body(new TopicPatchDTO(null, false, null, null, null, 0))
                .when().patch(PATH + "/1").then().statusCode(428);
        given().header("If-Match", "\"999\"").contentType(ContentType.JSON).body(new TopicPatchDTO(null, false, null, null, null, 0))
                .when().patch(PATH + "/1").then().statusCode(409);
    }

    // --- delete, trigger ---

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_DELETE" })
    void deleteRemovesTheTopic() {
        Topic topic = topic(1L, "Initial Setup Topic");
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic));
        when(session.contains(topic)).thenReturn(true);

        given().header("If-Match", "\"0\"").when().delete(PATH + "/1").then().statusCode(204);

        verify(session).remove(topic);
    }

    @Test
    @TestSecurity(user = ALL_TOPIC_ROLES_USER, roles = { "user", "TOPIC_TRIGGER_RUN" })
    void triggeringARunStartsABriefingJob() {
        Topic topic = topic(1L, "Initial Setup Topic");
        when(Topic.findByIdAndUserWithSources(1L, OWNER)).thenReturn(Optional.of(topic));
        BackgroundJob job = new BackgroundJob();
        job.id = UUID.randomUUID();
        when(backgroundJobService.triggerTopicBriefingJob(topic, OWNER.id)).thenReturn(job);

        given().contentType(ContentType.JSON).when().post(PATH + "/1/commands/generate-briefing")
                .then().statusCode(202)
                .header("Location", containsString("/api/v1/jobs/" + job.id));
    }

    private static Topic topic(Long id, String name) {
        return TopicBuilder.aTopic().withId(id).withName(name).withUser(OWNER).build();
    }

    private static ContentSource source(Long id, String identifier) {
        return ContentSourceBuilder.aContentSource().withId(id).withSourceIdentifier(identifier).build();
    }

    @SuppressWarnings("unchecked")
    private static PanacheQuery<Topic> topicQuery(Topic... topics) {
        PanacheQuery<Topic> query = mock(PanacheQuery.class);
        when(query.count()).thenReturn((long) topics.length);
        when(query.page(anyInt(), anyInt())).thenReturn(query);
        when(query.list()).thenReturn(List.of(topics));
        return query;
    }
}
