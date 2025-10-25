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

        // Mock the executor to run tasks immediately on the same thread for execute
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run(); // Execute immediately
            return null;
        }).when(managedExecutor).execute(any(Runnable.class));
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
        user.name = "Test User";
        user.active = true;
        List<User> activeUsers = Collections.singletonList(user);

        Topic topic = new Topic();
        topic.id = 1L;
        topic.name = "Test Topic";
        topic.user = user;
        ContentSource contentSource = new ContentSource();
        Set<ContentSource> contentSources = new HashSet<>();
        contentSources.add(contentSource);
        topic.contentSources = contentSources;
        List<Topic> allTopics = Collections.singletonList(topic);

        mockedUser.when(User::listAllActive).thenReturn(activeUsers);
        mockedTopic.when(() -> Topic.findByUsers(activeUsers)).thenReturn(allTopics);

        ContentItem contentItem = new ContentItem(); // Simplified ContentItem
        List<ContentItem> fetchedItems = Collections.singletonList(contentItem);

        when(contentItemFetcher.fetchItems(any(ContentSource.class), any(LocalDate.class), eq(null), eq(null)))
                .thenReturn(fetchedItems);
        when(asyncSummarizationService.summarizeAsync(any(ContentItem.class)))
                .thenReturn(Uni.createFrom().voidItem());

        // When
        orchestratorService.runOrchestration().await().indefinitely();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUsers(eq(activeUsers)), times(1));
        verify(contentItemFetcher, times(1)).fetchItems(eq(contentSource), any(LocalDate.class), eq(null), eq(null));
        verify(asyncSummarizationService, times(1)).summarizeAsync(contentItem);
        verify(briefingService, times(1)).generateBriefing(eq(topic), any(LocalDate.class));
    }
}
