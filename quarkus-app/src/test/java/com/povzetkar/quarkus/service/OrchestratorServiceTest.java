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
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
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

    private User testUser;
    private Topic testTopic;
    private ContentSource testContentSource;
    private ContentItem testContentItem;

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

        // Stub runAsync to return a completed CompletableFuture
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }).when(managedExecutor).runAsync(any(Runnable.class));

        // Setup common test data
        testUser = new User();
        testUser.id = 1L;
        testUser.name = "Test User";
        testUser.active = true;

        testTopic = new Topic();
        testTopic.id = 1L;
        testTopic.name = "Test Topic";
        testTopic.user = testUser;
        testContentSource = new ContentSource();
        testContentSource.id = 1L;
        testContentSource.name = "Test Content Source";
        Set<ContentSource> contentSources = new HashSet<>();
        contentSources.add(testContentSource);
        testTopic.contentSources = contentSources;

        testUser.topics.add(testTopic);

        testContentItem = new ContentItem(); // Simplified ContentItem
    }

    @AfterEach
    void tearDown() {
        mockedUser.close();
        mockedTopic.close();
    }

    private void setupCommonMocks(LocalDate orchestrationDate) {
        mockedUser.when(User::listAllActive).thenReturn(Collections.singletonList(testUser));
        mockedTopic.when(() -> Topic.findByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));

        when(contentItemFetcher.fetchItems(any(ContentSource.class), eq(orchestrationDate), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(testContentItem));
        when(asyncSummarizationService.summarizeAsync(any(ContentItem.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(briefingService.generateBriefing(any(Topic.class), eq(orchestrationDate)))
                .thenReturn(null); // Return null or a mock Briefing if needed
    }

    @Test
    void testOrchestratorRunsScheduled() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        setupCommonMocks(expectedDate);

        // When
        orchestratorService.runOrchestrationScheduled().await().indefinitely();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUsers(eq(Collections.singletonList(testUser))), times(1));
        verify(contentItemFetcher, times(1)).fetchItems(eq(testContentSource), eq(expectedDate), eq(null), eq(null));
        verify(asyncSummarizationService, times(1)).summarizeAsync(testContentItem);
        verify(briefingService, times(1)).generateBriefing(testTopic, expectedDate);
    }

    @Test
    void testRunOrchestrationManually_defaultCurrentDate() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        setupCommonMocks(expectedDate);

        // When
        orchestratorService.runOrchestrationManually(null, null).await().indefinitely();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUsers(eq(Collections.singletonList(testUser))), times(1));
        verify(contentItemFetcher, times(1)).fetchItems(eq(testContentSource), eq(expectedDate), eq(null), eq(null));
        verify(asyncSummarizationService, times(1)).summarizeAsync(testContentItem);
        verify(briefingService, times(1)).generateBriefing(testTopic, expectedDate);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() {
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(5);
        setupCommonMocks(pastDate);

        // When
        orchestratorService.runOrchestrationManually(pastDate, null).await().indefinitely();

        // Then
        mockedUser.verify(User::listAllActive, times(1));
        mockedTopic.verify(() -> Topic.findByUsers(eq(Collections.singletonList(testUser))), times(1));
        verify(contentItemFetcher, times(1)).fetchItems(eq(testContentSource), eq(pastDate), eq(null), eq(null));
        verify(asyncSummarizationService, times(1)).summarizeAsync(testContentItem);
        verify(briefingService, times(1)).generateBriefing(testTopic, pastDate);
    }

    @Test
    void testRunOrchestrationManually_dateRange() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        long numOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Mock static methods once per day in the range
        mockedUser.when(User::listAllActive).thenReturn(Collections.singletonList(testUser));
        mockedTopic.when(() -> Topic.findByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));

        // Mock the dependencies that are called for each day
        when(contentItemFetcher.fetchItems(any(ContentSource.class), any(LocalDate.class), eq(null), eq(null)))
                .thenReturn(Collections.singletonList(testContentItem));
        when(asyncSummarizationService.summarizeAsync(any(ContentItem.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(briefingService.generateBriefing(any(Topic.class), any(LocalDate.class)))
                .thenReturn(null);

        // When
        orchestratorService.runOrchestrationManually(startDate, endDate).await().indefinitely();

        // Then
        // Verify static methods are called once per day in the range
        mockedUser.verify(User::listAllActive, times((int) numOfDays));
        mockedTopic.verify(() -> Topic.findByUsers(eq(Collections.singletonList(testUser))), times((int) numOfDays));

        // Verify fetchItems, summarizeAsync, generateBriefing are called for each day
        verify(contentItemFetcher, times((int) numOfDays)).fetchItems(eq(testContentSource), any(LocalDate.class), eq(null), eq(null));
        verify(asyncSummarizationService, times((int) numOfDays)).summarizeAsync(testContentItem);
        verify(briefingService, times((int) numOfDays)).generateBriefing(eq(testTopic), any(LocalDate.class));
    }

    @Test
    void testRunOrchestrationManually_futureDate_throwsException() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(1);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestratorService.runOrchestrationManually(futureDate, null).await().indefinitely(), "Orchestration cannot be run for future dates.");
    }

    @Test
    void testRunOrchestrationManually_startDateAfterEndDate_throwsException() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().minusDays(5);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestratorService.runOrchestrationManually(startDate, endDate).await().indefinitely(), "Start date cannot be after end date.");
    }
}
