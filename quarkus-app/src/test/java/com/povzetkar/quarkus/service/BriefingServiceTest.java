package com.povzetkar.quarkus.service;

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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
class BriefingServiceTest {

    @Inject
    BriefingService briefingService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private Topic testTopic;

    @BeforeEach
    @Transactional
    void setUp() {
        // Clean up database before each test to ensure isolation
        tearDown();
        setupTestData();
    }

    void setupTestData() {
        User testUser = new User();
        testUser.username = "test_user_" + UUID.randomUUID();
        testUser.name = "Test User";
        testUser.active = true;
        testUser.persist();

        testTopic = new Topic();
        testTopic.name = "Test Topic";
        testTopic.user = testUser;
        testTopic.persist();

        ContentSource testContentSource = new ContentSource();
        testContentSource.sourceIdentifier = "test_channel_id_" + UUID.randomUUID();
        testContentSource.sourceTypeId = SourceTypeEnum.YOUTUBE.getId();
        testContentSource.name = "Test Channel";
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
        contentItem.contentSource = (ContentSource) ContentSource.listAll().getFirst();
        
        Summary summary = new Summary("Summary text 1.", contentItem, briefingDate);

        QuarkusTransaction.requiringNew().run(() -> {
            contentItem.persist();
            summary.persist();
        });

        when(videoSummaryService.createBriefingFromSummaries(any(), any())).thenReturn("Meta-summary from GenAI");

        // When
        Briefing briefing = briefingService.generateBriefing(testTopic, briefingDate);

        // Then
        assertNotNull(briefing);
        assertEquals(1, Briefing.count());
        Briefing createdBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals("Meta-summary from GenAI", createdBriefing.metaSummary);
        assertEquals(1, createdBriefing.summaries.size());
        assertEquals(summary.id, createdBriefing.summaries.iterator().next().id);
    }
}
