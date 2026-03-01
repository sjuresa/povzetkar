package com.povzetkar.quarkus.service;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.OrchestrationLock;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestrationManagerTest {

    @Inject
    OrchestrationManager orchestrationManager;

    @InjectMock
    OrchestrationLockService orchestrationLockService;

    @InjectMock
    TopicProcessor topicProcessor;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @InjectMock
    ManagedExecutor managedExecutor;

    @InjectMock
    JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;

    @BeforeEach
    @Transactional
    void setUp() {
        Mockito.reset(orchestrationLockService, topicProcessor, managedExecutor, videoSummaryService, justInTimeSummarizationScheduler);
        
        // Clean up database before each test to ensure isolation
        tearDown();

        // Mock the executor to run tasks immediately on the same thread for runAsync
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }).when(managedExecutor).runAsync(any(Runnable.class));

        // Add the missing mock that was causing NPEs
        when(videoSummaryService.acquireClient()).thenReturn(mock(GenAiClient.class));
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
        OrchestrationLock.deleteAll();
    }

    private User setupUser() {
        User user = UserBuilder.aUser()
			.withUsername("test_user")
			.withEmail("test_user@example.com")
			.withName("Test User")
			.build();
        user.active = true;
        return user;
    }

    private Topic setupTopic(User user) {
        Topic topic = TopicBuilder.aTopic()
			.withName("Test Topic")
			.withUser(user)
			.build();
        topic.enabled = true;
        return topic;
    }

    private ContentSource setupContentSource() {
        return ContentSourceBuilder.aContentSource()
			.withSourceIdentifier("test_source")
			.withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
			.withName("Test Channel")
			.build();
    }

    @Test
    @Transactional
    void testRunOrchestrationScheduled() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        User user = setupUser();
        user.persist();
        Topic topic = setupTopic(user);
        topic.persist();
        ContentSource contentSource = setupContentSource();
        contentSource.persist();
        topic.contentSources.add(contentSource);
        topic.persist();
        
        // Mock external service calls
        doNothing().when(orchestrationLockService).acquireLock(expectedDate);
        doNothing().when(orchestrationLockService).releaseLock(expectedDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem()); // Mock for retry logic

        // When
        orchestrationManager.runOrchestrationScheduled().await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(0, Briefing.count()); // No briefing should be generated by this scheduled task
        
        // Verify external service calls
        verify(orchestrationLockService, times(1)).acquireLock(expectedDate);
        verify(topicProcessor, times(1)).processTopicUni(topic, expectedDate);
        verify(orchestrationLockService, times(1)).releaseLock(expectedDate);
    }

    @Test
    void testRunBriefingGenerationScheduled() { // Removed @Transactional
        // Given
        LocalDate expectedDate = LocalDate.now();
        
        // Setup entities within a transaction
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();
            Topic topic = setupTopic(user);
            topic.persist();
            ContentSource contentSource = setupContentSource();
            contentSource.persist();
            topic.contentSources.add(contentSource);
            topic.persist();

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/1";
            contentItem.rawContent = "raw content";
            contentItem.title = "Test Content Item Title";
            contentItem.publishedAt = Instant.now();
            contentItem.contentSource = contentSource;
            contentItem.processingStatus = ProcessingStatus.COMPLETED;
            contentItem.persist();

            Summary summary = new Summary("Test Summary", contentItem, expectedDate);
            summary.persist();
        });

        // Mock VideoSummaryService for DefaultBriefingGenerator
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");

        // When
        orchestrationManager.runBriefingGenerationScheduled().await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(1, Briefing.count());
        Briefing createdBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals(expectedDate, createdBriefing.briefingDate);
        assertEquals(1, createdBriefing.summaries.size());
        Summary persistedSummary = (Summary) Summary.listAll().getFirst();
        assertEquals(persistedSummary.id, createdBriefing.summaries.iterator().next().id);
    }

    @Test
    void testRunOrchestrationManually_defaultCurrentDate() { // Removed @Transactional
        // Given
        LocalDate expectedDate = LocalDate.now();
        var request = new OrchestrationRequest(null, null, true, true, true, false);
        
        // Setup entities within a transaction
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();
            Topic topic = setupTopic(user);
            topic.persist();
            ContentSource contentSource = setupContentSource();
            contentSource.persist();
            topic.contentSources.add(contentSource);
            topic.persist();

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/1";
            contentItem.rawContent = "raw content";
            contentItem.title = "Test Content Item Title";
            contentItem.publishedAt = Instant.now();
            contentItem.contentSource = contentSource;
            contentItem.processingStatus = ProcessingStatus.COMPLETED;
            contentItem.persist();

            Summary summary = new Summary("Test Summary", contentItem, expectedDate);
            summary.persist();
        });

        // Mock external service calls
        doNothing().when(orchestrationLockService).acquireLock(expectedDate);
        doNothing().when(orchestrationLockService).releaseLock(expectedDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem()); // Mock for retry logic

        // Mock VideoSummaryService for DefaultBriefingGenerator
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(1, Briefing.count());
        Briefing createdBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals(expectedDate, createdBriefing.briefingDate);
        assertEquals(1, createdBriefing.summaries.size());
        Summary persistedSummary = (Summary) Summary.listAll().getFirst();
        assertEquals(persistedSummary.id, createdBriefing.summaries.iterator().next().id);

        // Verify external service calls
        verify(orchestrationLockService, times(1)).acquireLock(expectedDate);
        verify(topicProcessor, times(1)).processTopicUni(any(Topic.class), eq(expectedDate));
        verify(orchestrationLockService, times(1)).releaseLock(expectedDate);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() { // Removed @Transactional
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(5);
        var request = new OrchestrationRequest(pastDate, null, true, true, true, false);
        
        // Setup entities within a transaction
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();
            Topic topic = setupTopic(user);
            topic.persist();
            ContentSource contentSource = setupContentSource();
            contentSource.persist();
            topic.contentSources.add(contentSource);
            topic.persist();

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/1";
            contentItem.rawContent = "raw content";
            contentItem.title = "Test Content Item Title";
            contentItem.publishedAt = Instant.now();
            contentItem.contentSource = contentSource;
            contentItem.processingStatus = ProcessingStatus.COMPLETED;
            contentItem.persist();

            Summary summary = new Summary("Test Summary", contentItem, pastDate);
            summary.persist();
        });

        // Mock external service calls
        doNothing().when(orchestrationLockService).acquireLock(pastDate);
        doNothing().when(orchestrationLockService).releaseLock(pastDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem()); // Mock for retry logic

        // Mock VideoSummaryService for DefaultBriefingGenerator
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(1, Briefing.count());
        Briefing createdBriefing = (Briefing) Briefing.listAll().getFirst();
        assertEquals(pastDate, createdBriefing.briefingDate);
        assertEquals(1, createdBriefing.summaries.size());
        Summary persistedSummary = (Summary) Summary.listAll().getFirst();
        assertEquals(persistedSummary.id, createdBriefing.summaries.iterator().next().id);

        // Verify external service calls
        verify(orchestrationLockService, times(1)).acquireLock(pastDate);
        verify(topicProcessor, times(1)).processTopicUni(any(Topic.class), eq(pastDate));
        verify(orchestrationLockService, times(1)).releaseLock(pastDate);
    }

    @Test
    void testRunOrchestrationManually_dateRange() { // Removed @Transactional
        // Given
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        var request = new OrchestrationRequest(startDate, endDate, true, true, true, false);
        long numOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;
        
        // Setup entities within a transaction
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();
            Topic topic = setupTopic(user);
            topic.persist();
            ContentSource contentSource = setupContentSource();
            contentSource.persist();
            topic.contentSources.add(contentSource);
            topic.persist();

            // Create summaries for each day in the range
            for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
                ContentItem contentItem = new ContentItem();
                contentItem.sourceUrl = "http://example.com/" + date.getDayOfMonth();
                contentItem.rawContent = "raw content " + date.getDayOfMonth();
                contentItem.title = "Test Content Item Title " + date.getDayOfMonth();
                contentItem.publishedAt = Instant.now();
                contentItem.contentSource = contentSource;
                contentItem.processingStatus = ProcessingStatus.COMPLETED;
                contentItem.persist();

                Summary summary = new Summary("Test Summary " + date.getDayOfMonth(), contentItem, date);
                summary.persist();
            }
        });

        // Mock external service calls for each day in the range
        doNothing().when(orchestrationLockService).acquireLock(any(LocalDate.class));
        doNothing().when(orchestrationLockService).releaseLock(any(LocalDate.class));
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem()); // Mock for retry logic

        // Mock VideoSummaryService for DefaultBriefingGenerator
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(numOfDays, Briefing.count());
        for (LocalDate date = startDate; !date.isAfter(endDate); date = date.plusDays(1)) {
            Briefing briefing = Briefing.findByTopicAndDate((Topic) Topic.listAll().getFirst(), date).orElseThrow();
            assertEquals(date, briefing.briefingDate);
            assertEquals(1, briefing.summaries.size());
        }

        // Verify external service calls
        verify(orchestrationLockService, times((int) numOfDays)).acquireLock(any(LocalDate.class));
        verify(topicProcessor, times((int) numOfDays)).processTopicUni(any(Topic.class), any(LocalDate.class));
        verify(orchestrationLockService, times((int) numOfDays)).releaseLock(any(LocalDate.class));
    }

    @Test
    void testRunOrchestrationManually_futureDate_throwsException() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(1);
        var request = new OrchestrationRequest(futureDate, null, true, true, true, false);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(request), "Orchestration cannot be run for future dates.");
    }

    @Test
    void testRunOrchestrationManually_startDateAfterEndDate_throwsException() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().minusDays(5);
        var request = new OrchestrationRequest(startDate, endDate, true, true, true, false);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(request), "Start date cannot be after end date.");
    }

    @Test
    @Transactional
    void testOrchestrationFlow_concurrentRunPrevention() {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        var request = new OrchestrationRequest(orchestrationDate, orchestrationDate, true, true, true, false);
        User user = setupUser();
        user.persist();
        Topic topic = setupTopic(user);
        topic.persist();
        ContentSource contentSource = setupContentSource();
        contentSource.persist(); // Persist contentSource in the main transaction
        ContentSource.flush(); // Ensure contentSource is flushed
        topic.contentSources.add(contentSource);
        topic.persist();

        doThrow(new OrchestrationLockService.OrchestrationLockException("Lock already held"))
                .when(orchestrationLockService).acquireLock(orchestrationDate);

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify real database interactions
        assertEquals(0, Briefing.count()); // No briefing should be generated

        // Verify external service calls
        verify(orchestrationLockService, times(1)).acquireLock(orchestrationDate);
        verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
        verify(orchestrationLockService, never()).releaseLock(orchestrationDate);
    }

    @Transactional
    @ParameterizedTest
    @EnumSource(value = ProcessingStatus.class, names = {"RETRY_LATER", "PENDING"})
    void testRunOrchestration_ReprocessesStuckItems(ProcessingStatus initialStatus) {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        // Setup all test data within a single new transaction
        final ContentItem stuckItem = QuarkusTransaction.requiringNew().call(() -> {
            User user = setupUser();
            user.persist();

            Topic topic = setupTopic(user);
            topic.persist();

            ContentSource contentSource = setupContentSource();
            contentSource.persist();

            topic.contentSources.add(contentSource);

            ContentItem item = new ContentItem();
            item.sourceUrl = "http://example.com/" + initialStatus.name().toLowerCase();
            item.rawContent = "raw content for " + initialStatus.name();
            item.title = initialStatus.name() + " Item Title";
            item.publishedAt = Instant.now();
            item.contentSource = contentSource;
            item.processingStatus = initialStatus; // Simulate stuck item
            item.persist();
            return item;
        });

        doNothing().when(orchestrationLockService).acquireLock(orchestrationDate);
        doNothing().when(orchestrationLockService).releaseLock(orchestrationDate);

        // Mock topicProcessor.processTopicUni to return a completed Uni
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // Mock the scheduler to simulate successful reprocessing
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenAnswer(invocation -> {
                    List<ContentItem> items = invocation.getArgument(0);
                    LocalDate date = invocation.getArgument(1);
                    QuarkusTransaction.requiringNew().run(() -> {
                        for (ContentItem item : items) {
                            ContentItem managedItem = ContentItem.findById(item.id);
                            if (managedItem != null) {
                                ContentItem.updateProcessingStatus(managedItem.id, ProcessingStatus.COMPLETED);
                                Summary summary = new Summary("Generated Summary for " + managedItem.title, managedItem, date);
                                summary.persist();
                            }
                        }
                    });
                    return Uni.createFrom().voidItem();
                });

        // When
        orchestrationManager.runOrchestrationScheduled().await().indefinitely();

        // Then
        // Clear the persistence context to ensure we load the latest state from the database
        ContentItem.getEntityManager().clear();

        // Verify that the scheduler was called with the stuck item
        verify(justInTimeSummarizationScheduler, times(1)).schedule(List.of(stuckItem), orchestrationDate);

        // Verify ContentItem status
        ContentItem updatedItem = ContentItem.findById(stuckItem.id);
        assertEquals(ProcessingStatus.COMPLETED, updatedItem.processingStatus);

        // Verify Summary creation
        List<Summary> summaries = Summary.list("contentItem", updatedItem);
        assertEquals(1, summaries.size());
        assertEquals("Generated Summary for " + updatedItem.title, summaries.getFirst().summaryText);
    }

    @Test
    @Transactional
    void testRunOrchestrationManually_skipsDisabledTopics() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        var request = new OrchestrationRequest(expectedDate, expectedDate, true, true, true, false);
        User user = setupUser();
        user.persist();
        Topic topic = setupTopic(user);
        topic.enabled = false; // Disable the topic
        topic.persist();
        ContentSource contentSource = setupContentSource();
        contentSource.persist();
        topic.contentSources.add(contentSource);
        topic.persist();

        // Mock external service calls
        doNothing().when(orchestrationLockService).acquireLock(expectedDate);
        doNothing().when(orchestrationLockService).releaseLock(expectedDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify that topicProcessor was NOT called
        verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
        
        // Verify lock was acquired and released
        verify(orchestrationLockService, times(1)).acquireLock(expectedDate);
        verify(orchestrationLockService, times(1)).releaseLock(expectedDate);
    }

    @Test
    @Transactional
    void testRunOrchestrationManually_granularExecution_summarizeOnly() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        var request = new OrchestrationRequest(expectedDate, expectedDate, false, true, false, false);
        User user = setupUser();
        user.persist();
        Topic topic = setupTopic(user);
        topic.persist();
        ContentSource contentSource = setupContentSource();
        contentSource.persist();
        topic.contentSources.add(contentSource);
        topic.persist();

        // Mock external service calls
        doNothing().when(orchestrationLockService).acquireLock(expectedDate);
        doNothing().when(orchestrationLockService).releaseLock(expectedDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify content orchestration ran
        verify(topicProcessor, times(1)).processTopicUni(topic, expectedDate);
        
        // Verify briefing generation did NOT run (no briefings created)
        assertEquals(0, Briefing.count());
    }

    @Test
    void testRunOrchestrationManually_granularExecution_briefingOnly() { // Removed @Transactional
        // Given
        LocalDate expectedDate = LocalDate.now();
        var request = new OrchestrationRequest(expectedDate, expectedDate, false, false, true, false);
        
        // Setup entities within a transaction
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();
            Topic topic = setupTopic(user);
            topic.persist();
            ContentSource contentSource = setupContentSource();
            contentSource.persist();
            topic.contentSources.add(contentSource);
            topic.persist();

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/1";
            contentItem.rawContent = "raw content";
            contentItem.title = "Test Content Item Title";
            contentItem.publishedAt = Instant.now();
            contentItem.contentSource = contentSource;
            contentItem.processingStatus = ProcessingStatus.COMPLETED;
            contentItem.persist();

            Summary summary = new Summary("Test Summary", contentItem, expectedDate);
            summary.persist();
        });

        // Mock VideoSummaryService for DefaultBriefingGenerator
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Meta-summary");

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify content orchestration did NOT run
        verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
        
        // Verify briefing generation ran
        assertEquals(1, Briefing.count());
    }

    @Test
    @Transactional
    void testRunOrchestrationManually_granularExecution_pendingOnly() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        var request = new OrchestrationRequest(expectedDate, expectedDate, true, false, false, false);
        
        // Setup entities
        User user = setupUser();
        user.persist();
        Topic topic = setupTopic(user);
        topic.persist();
        ContentSource contentSource = setupContentSource();
        contentSource.persist();
        topic.contentSources.add(contentSource);
        topic.persist();
        
        ContentItem item = new ContentItem();
        item.sourceUrl = "http://example.com/pending";
        item.rawContent = "raw content";
        item.title = "Pending Item";
        item.publishedAt = Instant.now();
        item.contentSource = contentSource;
        item.processingStatus = ProcessingStatus.PENDING;
        item.persist();

        // Mock external service calls
        when(justInTimeSummarizationScheduler.schedule(anyList(), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // Then
        // Verify pending processing ran
        verify(justInTimeSummarizationScheduler, times(1)).schedule(List.of(item), expectedDate);
        
        // Verify content orchestration did NOT run
        verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
        
        // Verify briefing generation did NOT run
        assertEquals(0, Briefing.count());
    }

    @Test
    @Transactional
    void testRunOrchestration_IgnoresStuckItemsForDisabledTopics() {
        // Given
        LocalDate orchestrationDate = LocalDate.now();

        // Setup a disabled topic with a pending item
        QuarkusTransaction.requiringNew().run(() -> {
            User user = setupUser();
            user.persist();

            Topic disabledTopic = setupTopic(user);
            disabledTopic.enabled = false;
            disabledTopic.persist();

            ContentSource contentSource = setupContentSource();
            contentSource.persist();

            disabledTopic.contentSources.add(contentSource);

            ContentItem item = new ContentItem();
            item.sourceUrl = "http://example.com/pending_disabled";
            item.rawContent = "raw content";
            item.title = "Pending Item on Disabled Topic";
            item.publishedAt = Instant.now();
            item.contentSource = contentSource;
            item.processingStatus = ProcessingStatus.PENDING;
            item.persist();
        });

        doNothing().when(orchestrationLockService).acquireLock(orchestrationDate);
        doNothing().when(orchestrationLockService).releaseLock(orchestrationDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        orchestrationManager.runOrchestrationScheduled().await().indefinitely();

        // Then
        // Verify that the scheduler was NOT called because the topic is disabled
        verify(justInTimeSummarizationScheduler, never()).schedule(anyList(), any(LocalDate.class));
    }
}
