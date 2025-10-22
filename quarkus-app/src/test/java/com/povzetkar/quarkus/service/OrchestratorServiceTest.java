package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
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

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestratorServiceTest {

    @Inject
    OrchestratorService orchestratorService;

    @InjectMock
    ContentItemFetcher contentItemFetcher;

    @InjectMock
    AsyncSummarizationService asyncSummarizationService;

    @InjectMock
    BriefingService briefingService;

    @InjectMock
    ManagedExecutor managedExecutor;

    private MockedStatic<User> mockedUser;
    private MockedStatic<Topic> mockedTopic;

    @BeforeEach
    void setUp() {
        Mockito.reset(contentItemFetcher, asyncSummarizationService, briefingService, managedExecutor);
        mockedUser = Mockito.mockStatic(User.class);
        mockedTopic = Mockito.mockStatic(Topic.class);
    }

    @AfterEach
    void tearDown() {
        mockedUser.close();
        mockedTopic.close();
    }

    @Test
    void testOrchestratorRunsScheduledTasksInParallel() {
        // Given
        User user1 = new User();
        user1.id = 1L;
        user1.username = "user1_" + UUID.randomUUID();
        user1.name = "User One";

        Topic topic1 = new Topic();
        topic1.id = 1L;
        topic1.name = "Topic One";
        topic1.user = user1;
        topic1.contentSources = Set.of(new ContentSource());

        Topic topic2 = new Topic();
        topic2.id = 2L;
        topic2.name = "Topic Two";
        topic2.user = user1;
        topic2.contentSources = Set.of(new ContentSource());

        User user2 = new User();
        user2.id = 2L;
        user2.username = "user2_" + UUID.randomUUID();
        user2.name = "User Two";

        Topic topic3 = new Topic();
        topic3.id = 3L;
        topic3.name = "Topic Three";
        topic3.user = user2;
        topic3.contentSources = Set.of(new ContentSource());

        mockedUser.when(User::listAllActive).thenReturn(List.of(user1, user2));
        mockedTopic.when(() -> Topic.findByUser(user1)).thenReturn(List.of(topic1, topic2));
        mockedTopic.when(() -> Topic.findByUser(user2)).thenReturn(List.of(topic3));

        ContentItem contentItem = new ContentItem();
        List<ContentItem> fetchedItems = Collections.singletonList(contentItem);

        when(contentItemFetcher.fetchItems(any(), any(), any(), any())).thenReturn(fetchedItems);
        when(asyncSummarizationService.summarizeAsync(any())).thenReturn(Uni.createFrom().voidItem());

        // Mock the inner parallel fetch
        when(managedExecutor.supplyAsync(any(Supplier.class)))
            .thenAnswer(invocation -> CompletableFuture.completedFuture(invocation.getArgument(0, Supplier.class).get()));

        // Mock the outer parallel topic processing
        when(managedExecutor.runAsync(any(Runnable.class)))
            .thenAnswer(invocation -> {
                invocation.getArgument(0, Runnable.class).run();
                return CompletableFuture.completedFuture(null);
            });

        // When
        orchestratorService.runOrchestration();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUser(user1), times(1));
        mockedTopic.verify(() -> Topic.findByUser(user2), times(1));

        // Verify that a task was submitted for each of the 3 topics
        verify(managedExecutor, times(3)).runAsync(any(Runnable.class));

        // Verify inner logic was called for each topic
        verify(contentItemFetcher, times(3)).fetchItems(any(), any(), any(), any());
        verify(asyncSummarizationService, times(3)).summarizeAsync(any());
        verify(briefingService, times(1)).generateBriefing(eq(topic1), any());
        verify(briefingService, times(1)).generateBriefing(eq(topic2), any());
        verify(briefingService, times(1)).generateBriefing(eq(topic3), any());
    }
}
