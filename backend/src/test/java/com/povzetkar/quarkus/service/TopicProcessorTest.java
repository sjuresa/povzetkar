package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class TopicProcessorTest {

    @Inject
    TopicProcessor topicProcessor;

    @InjectMock
    ContentItemFetcher contentItemFetcher;

    @InjectMock
    JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;

    @InjectMock
    ManagedExecutor managedExecutor;

    private MockedStatic<User> mockedUser;
    private MockedStatic<Topic> mockedTopic;

    private User testUser;
    private Topic testTopic;
    private ContentSource testContentSource;
    private ContentItem testContentItem;
    private final ProcessingContext runContext = ProcessingContext.automatic(java.time.Instant.now(), java.time.Duration.ofMinutes(15));

    @BeforeEach
    void setUp() {
        Mockito.reset(contentItemFetcher, justInTimeSummarizationScheduler, managedExecutor);
        mockedUser = Mockito.mockStatic(User.class);
        mockedTopic = Mockito.mockStatic(Topic.class);

        // Mock the executor to run tasks immediately on the same thread for execute
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run(); // Execute immediately
            return null;
        }).when(managedExecutor).execute(any(Runnable.class));

        // Stub runAsync to return a completed CompletableFuture
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }).when(managedExecutor).runAsync(any(Runnable.class));

        // Setup common test data
        testUser = UserBuilder.aUser()
                .withId(1L)
                .withName("Test User")
                .withUsername("test_user")
                .withEmail("test_user@example.com")
                .build();

        testTopic = TopicBuilder.aTopic()
                .withId(1L)
                .withName("Test Topic")
                .withUser(testUser)
                .build();

        testContentSource = ContentSourceBuilder.aContentSource()
                .withId(1L)
                .withName("Test Content Source")
                .build();
        Set<ContentSource> contentSources = new HashSet<>();
        contentSources.add(testContentSource);
        testTopic.contentSources = contentSources;

        testUser.topics.add(testTopic);

        testContentItem = new ContentItem();
        testContentItem.processingStatus = ProcessingStatus.PENDING;
    }

    @AfterEach
    void tearDown() {
        mockedUser.close();
        mockedTopic.close();
    }

    private void setupCommonMocks(LocalDate orchestrationDate) {
        mockedUser.when(User::listAllEnabled).thenReturn(Collections.singletonList(testUser));
        mockedTopic.when(() -> Topic.findEnabledByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));

        when(contentItemFetcher.fetchItems(any(ContentSource.class), eq(orchestrationDate), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(testContentItem));
        when(justInTimeSummarizationScheduler.schedule(anyList(), eq(orchestrationDate), any(ProcessingContext.class)))
                .thenReturn(Uni.createFrom().voidItem());
    }

    @Test
    void testProcessTopicUni_success() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        setupCommonMocks(expectedDate);

        // When
        topicProcessor.processTopicUni(testTopic, expectedDate, runContext).await().indefinitely();

        // Then
        verify(contentItemFetcher, times(1)).fetchItems(testContentSource, expectedDate, null, null);
        verify(justInTimeSummarizationScheduler, times(1)).schedule(List.of(testContentItem), expectedDate, runContext);
    }

    @Test
    void testProcessTopicUni_skippedTooShortNotSummarized() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        ContentItem skippedItem = new ContentItem();
        skippedItem.processingStatus = ProcessingStatus.SKIPPED_TOO_SHORT;

        when(contentItemFetcher.fetchItems(any(ContentSource.class), eq(expectedDate), eq(null), eq(null)))
                .thenReturn(List.of(skippedItem, testContentItem));
        when(justInTimeSummarizationScheduler.schedule(anyList(), eq(expectedDate), any(ProcessingContext.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        topicProcessor.processTopicUni(testTopic, expectedDate, runContext).await().indefinitely();

        // Then — only PENDING item scheduled, SKIPPED_TOO_SHORT filtered out
        verify(justInTimeSummarizationScheduler, times(1)).schedule(List.of(testContentItem), expectedDate, runContext);
    }

    @Test
    void testProcessTopicUni_processPending_schedulesItemsDueInTopic() {
        // POV-222: the per-topic pending selection goes through the named query (due items only)
        LocalDate expectedDate = LocalDate.now();
        PanacheMock.mock(ContentItem.class);
        when(ContentItem.listDueInTopic(eq(1L), eq(expectedDate), any(Instant.class))).thenReturn(List.of(testContentItem));
        when(contentItemFetcher.fetchItems(any(ContentSource.class), eq(expectedDate), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());
        when(justInTimeSummarizationScheduler.schedule(anyList(), eq(expectedDate), any(ProcessingContext.class)))
                .thenReturn(Uni.createFrom().voidItem());

        topicProcessor.processTopicUni(testTopic, expectedDate, true, runContext).await().indefinitely();

        PanacheMock.verify(ContentItem.class).listDueInTopic(eq(1L), eq(expectedDate), any(Instant.class));
        verify(justInTimeSummarizationScheduler, times(1)).schedule(List.of(testContentItem), expectedDate, runContext);
    }

    @Test
    void testProcessTopicUni_noNewContent() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        mockedUser.when(User::listAllEnabled).thenReturn(Collections.singletonList(testUser));
        mockedTopic.when(() -> Topic.findEnabledByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));

        when(contentItemFetcher.fetchItems(any(ContentSource.class), eq(expectedDate), eq(null), eq(null)))
                .thenReturn(Collections.emptyList());

        // When
        topicProcessor.processTopicUni(testTopic, expectedDate, runContext).await().indefinitely();

        // Then
        verify(contentItemFetcher, times(1)).fetchItems(testContentSource, expectedDate, null, null);
        verifyNoInteractions(justInTimeSummarizationScheduler);
    }
}
