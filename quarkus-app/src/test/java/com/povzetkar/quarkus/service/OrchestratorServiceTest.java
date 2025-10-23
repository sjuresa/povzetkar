package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;

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

    private MockedStatic<User> mockedUser;
    private MockedStatic<Topic> mockedTopic;

    @BeforeEach
    void setUp() {
        Mockito.reset(contentItemFetcher, asyncSummarizationService, briefingService);
        mockedUser = Mockito.mockStatic(User.class);
        mockedTopic = Mockito.mockStatic(Topic.class);
    }

    @AfterEach
    void tearDown() {
        mockedUser.close();
        mockedTopic.close();
    }

    @Test
    void testOrchestrationProcessesAllTopics() {
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

        User user2 = new User();
        user2.id = 2L;
        user2.username = "user2_" + UUID.randomUUID();
        user2.name = "User Two";

        Topic topic2 = new Topic();
        topic2.id = 2L;
        topic2.name = "Topic Two";
        topic2.user = user2;
        topic2.contentSources = Set.of(new ContentSource());

        List<User> activeUsers = List.of(user1, user2);
        mockedUser.when(User::listAllActive).thenReturn(activeUsers);
        List<Topic> allTopics = List.of(topic1, topic2);
        mockedTopic.when(() -> Topic.findByUsers(activeUsers)).thenReturn(allTopics);

        ContentItem contentItem = new ContentItem();
        List<ContentItem> fetchedItems = Collections.singletonList(contentItem);

        when(contentItemFetcher.fetchItems(any(), any(), any(), any())).thenReturn(fetchedItems);
        when(asyncSummarizationService.summarizeAsync(any())).thenReturn(Uni.createFrom().voidItem());

        // When
        orchestratorService.runOrchestration();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUsers(activeUsers));

        // Verify that for each topic, the processing steps were called
        verify(contentItemFetcher, times(allTopics.size())).fetchItems(any(), any(), any(), any());
        verify(asyncSummarizationService, times(allTopics.size())).summarizeAsync(any());
        verify(briefingService, times(1)).generateBriefing(eq(topic1), any());
        verify(briefingService, times(1)).generateBriefing(eq(topic2), any());
    }
}
