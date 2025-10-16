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
    void testOrchestratorRunsScheduledTasks() {
        // Given
        User user = new User();
        user.id = 1L;
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

        // When
        orchestratorService.runOrchestration();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUser(eq(user)), times(1));
        verify(contentItemFetcher, times(1)).fetchItems(eq(contentSource), any(LocalDate.class), eq(null), eq(null));
        verify(asyncSummarizationService, times(1)).summarizeAsync(contentItem);
        verify(briefingService, times(1)).generateBriefing(eq(topic), any(LocalDate.class));
    }
}
