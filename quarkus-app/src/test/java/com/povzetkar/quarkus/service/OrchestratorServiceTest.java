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

import java.time.LocalDate;
import java.util.Collections;
import java.util.HashSet;
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
    void testOrchestratorRunsScheduledTasks() {
        // Given
        User user = new User();
        user.id = 1L;
        user.username = "test_user_" + UUID.randomUUID();
        user.name = "Test User";
        user.active = true;

        Topic topic = new Topic();
        topic.id = 1L;
        topic.name = "Test Topic";
        topic.user = user;

        ContentSource contentSource = new ContentSource();
        contentSource.id = 1L;
        contentSource.name = "Test Content Source";
        Set<ContentSource> contentSources = new HashSet<>();
        contentSources.add(contentSource);
        topic.contentSources = contentSources;

        user.topics.add(topic);

        mockedUser.when(User::listAllActive).thenReturn(Collections.singletonList(user));
        mockedTopic.when(() -> Topic.findByUser(any(User.class))).thenReturn(Collections.singletonList(topic));

        ContentItem contentItem = new ContentItem(); // Simplified ContentItem
        List<ContentItem> fetchedItems = Collections.singletonList(contentItem);

        when(contentItemFetcher.fetchItems(any(ContentSource.class), any(LocalDate.class), eq(null), eq(null)))
                .thenReturn(fetchedItems);
        when(asyncSummarizationService.summarizeAsync(any(ContentItem.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // Mock ManagedExecutor to run the Supplier immediately and return a completed future
        when(managedExecutor.supplyAsync(any(Supplier.class)))
            .thenAnswer(invocation -> {
                Supplier<List<ContentItem>> supplier = invocation.getArgument(0);
                return CompletableFuture.completedFuture(supplier.get()); // Execute the supplier and return its result
            });

        // When
        orchestratorService.runOrchestration();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUser(eq(user)), times(1));
        // Verify that managedExecutor.supplyAsync was called for each content source
        verify(managedExecutor, times(contentSources.size())).supplyAsync(any(Supplier.class));
        verify(contentItemFetcher, times(contentSources.size())).fetchItems(eq(contentSource), any(LocalDate.class), eq(null), eq(null));
        verify(asyncSummarizationService, times(fetchedItems.size())).summarizeAsync(contentItem);
        verify(briefingService, times(1)).generateBriefing(eq(topic), any(LocalDate.class));
    }
}
