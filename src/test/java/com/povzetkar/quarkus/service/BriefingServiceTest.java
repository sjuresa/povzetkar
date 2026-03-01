package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@QuarkusTest
class BriefingServiceTest {

    @Inject
    BriefingService briefingService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private Topic testTopic;
    private ContentSource testContentSource;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test to ensure isolation
        tearDown();
        setupTestData();
    }

    void setupTestData() {
        String uName = "test_user_" + UUID.randomUUID();
        User testUser = UserBuilder.aUser()
                .withUsername(uName)
                .withEmail(uName + "@example.com")
                .withName("Test User")
                .build();
        testUser.active = true;
        testUser.persist();

        testTopic = TopicBuilder.aTopic()
                .withName("Test Topic")
                .withUser(testUser)
                .build();
        testTopic.persist();

        testContentSource = ContentSourceBuilder.aContentSource()
                .withSourceIdentifier("test_channel_id_" + UUID.randomUUID())
                .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
                .withName("Test Channel")
                .build();
        testContentSource.persist();

        testTopic.contentSources.add(testContentSource);
        testTopic.persist();
    }

    @AfterEach
    @Transactional
    void tearDown() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
    }

    @Test
    @Transactional
    void testGenerateBriefing_CreatesAndPersistsBriefing() {
        // Given
        LocalDate briefingDate = LocalDate.now();
        ContentItem contentItem = new ContentItem();
        contentItem.sourceUrl = "http://example.com/" + UUID.randomUUID();
        contentItem.rawContent = "raw content";
        contentItem.publishedAt = Instant.now();
        contentItem.title = "Test Title";
        contentItem.contentSource = testContentSource;

        Summary summary = new Summary("Summary text 1.", contentItem, briefingDate);

        QuarkusTransaction.requiringNew().run(() -> {
            contentItem.persist();
            summary.persist();
        });

        String mockMetaSummary = "Meta-summary from GenAI";
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString())).thenReturn(mockMetaSummary);

        // When
        Briefing briefing = briefingService.generateBriefing(testTopic, briefingDate, List.of(summary), mockMetaSummary);

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        Briefing createdBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals(mockMetaSummary, createdBriefing.metaSummary);
        assertEquals(1, createdBriefing.summaries.size());
        assertEquals(summary.id, createdBriefing.summaries.iterator().next().id);
    }
}
