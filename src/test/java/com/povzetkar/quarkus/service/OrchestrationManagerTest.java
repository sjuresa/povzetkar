package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
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
    BriefingService briefingService;

    @InjectMock
    ManagedExecutor managedExecutor;

    private User testUser;
    private Topic testTopic;
    private Summary testSummary;
    private Briefing testBriefing;

    @BeforeEach
    void setUp() {
        Mockito.reset(orchestrationLockService, topicProcessor, briefingService, managedExecutor);
        PanacheMock.mock(User.class);
        PanacheMock.mock(Topic.class);
        PanacheMock.mock(Briefing.class);

        // Mock the executor to run tasks immediately on the same thread for runAsync
        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return CompletableFuture.completedFuture(null);
        }).when(managedExecutor).runAsync(any(Runnable.class));

        testUser = new User();
        testUser.id = 1L;
        testUser.name = "Test User";
        testUser.active = true;

        testTopic = new Topic();
        testTopic.id = 1L;
        testTopic.name = "Test Topic";
        testTopic.user = testUser;

        testUser.topics.add(testTopic);

        testSummary = new Summary();
        testSummary.id = 1L;
        testSummary.summaryText = "Test Summary";

        testBriefing = new Briefing();
        testBriefing.id = 1L;
        testBriefing.topic = testTopic;
        testBriefing.briefingDate = LocalDate.now();
        testBriefing.summaries.add(testSummary);
    }

    private void setupCommonMocks(LocalDate orchestrationDate) {
        Mockito.when(User.listAllActive()).thenReturn(Collections.singletonList(testUser));
        Mockito.when(Topic.findByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));
        Mockito.when(Topic.listAll()).thenReturn(Collections.singletonList(testTopic));
        doNothing().when(orchestrationLockService).acquireLock(orchestrationDate);
        doNothing().when(orchestrationLockService).releaseLock(orchestrationDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(briefingService.getSummariesForBriefing(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(testSummary));
        when(briefingService.generateBriefing(any(Topic.class), any(LocalDate.class), anyList())).thenReturn(testBriefing);
        when(Briefing.findByTopicAndDate(any(Topic.class), any(LocalDate.class))).thenReturn(Optional.of(testBriefing));
    }

    @Test
    void testRunOrchestrationScheduled() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        setupCommonMocks(expectedDate);

        // When
        orchestrationManager.runOrchestrationScheduled().await().indefinitely();

        // Then
        PanacheMock.verify(User.class, times(1)).listAllActive();
        PanacheMock.verify(Topic.class, times(1)).findByUsers(eq(Collections.singletonList(testUser)));
        verify(orchestrationLockService, times(1)).acquireLock(expectedDate);
        verify(topicProcessor, times(1)).processTopicUni(testTopic, expectedDate);
        verify(orchestrationLockService, times(1)).releaseLock(expectedDate);
        // Briefing generation is a separate scheduled task, so it should not be called here
        verify(briefingService, never()).generateBriefing(any(Topic.class), any(LocalDate.class), anyList());
    }

    @Test
    void testRunBriefingGenerationScheduled() {
        // Given
        LocalDate expectedDate = LocalDate.now();
        setupCommonMocks(expectedDate);

        // When
        orchestrationManager.runBriefingGenerationScheduled().await().indefinitely();

        // Then
        PanacheMock.verify(Topic.class, times(1)).findByUsers(anyList());
        verify(briefingService, times(1)).getSummariesForBriefing(testTopic, expectedDate);
        PanacheMock.verify(Briefing.class, times(1)).findByTopicAndDate(testTopic, expectedDate);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() {
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(5);
        setupCommonMocks(pastDate);

        // When
        orchestrationManager.runOrchestrationManually(pastDate, null).await().indefinitely();

        // Then
        try (MockedStatic<User> mockedUser = mockStatic(User.class);
             MockedStatic<Topic> mockedTopic = mockStatic(Topic.class);
             MockedStatic<Briefing> mockedBriefing = mockStatic(Briefing.class)) {
            mockedUser.when(User::listAllActive).thenReturn(Collections.singletonList(testUser));
            mockedTopic.when(() -> Topic.findByUsers(anyList())).thenReturn(Collections.singletonList(testTopic));
            mockedBriefing.when(() -> Briefing.findByTopicAndDate(any(Topic.class), any(LocalDate.class))).thenReturn(Optional.of(testBriefing));

            verify(orchestrationLockService, times(1)).acquireLock(pastDate);
            verify(topicProcessor, times(1)).processTopicUni(testTopic, pastDate);
            verify(orchestrationLockService, times(1)).releaseLock(pastDate);
            verify(briefingService, times(1)).getSummariesForBriefing(testTopic, pastDate);
        }
    }

    @Test
    void testRunOrchestrationManually_dateRange() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        long numOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        // Mock static methods once per day in the range
        Mockito.when(User.listAllActive()).thenReturn(Collections.singletonList(testUser));
        Mockito.when(Topic.findByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));
        Mockito.when(Topic.listAll()).thenReturn(Collections.singletonList(testTopic));

        // Mock the dependencies that are called for each day
        doNothing().when(orchestrationLockService).acquireLock(any(LocalDate.class));
        doNothing().when(orchestrationLockService).releaseLock(any(LocalDate.class));
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(briefingService.getSummariesForBriefing(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Collections.singletonList(testSummary));
        when(briefingService.generateBriefing(any(Topic.class), any(LocalDate.class), anyList())).thenReturn(testBriefing);
        Mockito.when(Briefing.findByTopicAndDate(any(Topic.class), any(LocalDate.class))).thenReturn(Optional.of(testBriefing));


        // When
        orchestrationManager.runOrchestrationManually(startDate, endDate).await().indefinitely();

        // Then
        // Verify static methods are called once per day in the range
        PanacheMock.verify(User.class, times((int) numOfDays * 2)).listAllActive();
        PanacheMock.verify(Topic.class, times((int) numOfDays * 2)).findByUsers(eq(Collections.singletonList(testUser)));
        PanacheMock.verify(Topic.class, times((int) numOfDays * 2)).findByUsers(anyList());

        // Verify acquireLock, processTopicUni, releaseLock are called for each day
        verify(orchestrationLockService, times((int) numOfDays)).acquireLock(any(LocalDate.class));
        verify(topicProcessor, times((int) numOfDays)).processTopicUni(eq(testTopic), any(LocalDate.class));
        verify(orchestrationLockService, times((int) numOfDays)).releaseLock(any(LocalDate.class));
        verify(briefingService, times((int) numOfDays)).getSummariesForBriefing(eq(testTopic), any(LocalDate.class));
        verify(briefingService, never()).generateBriefing(eq(testTopic), any(LocalDate.class), anyList());
        PanacheMock.verify(Briefing.class, times((int)numOfDays)).findByTopicAndDate(eq(testTopic), any(LocalDate.class));
    }

    @Test
    void testRunOrchestrationManually_futureDate_throwsException() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(1);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(futureDate, null).await().indefinitely(), "Orchestration cannot be run for future dates.");
    }

    @Test
    void testRunOrchestrationManually_startDateAfterEndDate_throwsException() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().minusDays(5);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(startDate, endDate).await().indefinitely(), "Start date cannot be after end date.");
    }

    @Test
    void testOrchestrationFlow_concurrentRunPrevention() {
        // Given
        LocalDate orchestrationDate = LocalDate.now();
        doThrow(new OrchestrationLockService.OrchestrationLockException("Lock already held"))
                .when(orchestrationLockService).acquireLock(orchestrationDate);

        // When
        orchestrationManager.runOrchestrationManually(orchestrationDate, orchestrationDate).await().indefinitely();

        // Then
        try (MockedStatic<User> mockedUser = mockStatic(User.class);
             MockedStatic<Topic> mockedTopic = mockStatic(Topic.class);
             MockedStatic<Briefing> mockedBriefing = mockStatic(Briefing.class)) {
            mockedUser.when(User::listAllActive).thenReturn(Collections.singletonList(testUser));
            mockedTopic.when(() -> Topic.findByUsers(anyList())).thenReturn(Collections.singletonList(testTopic));
            mockedBriefing.when(() -> Briefing.findByTopicAndDate(any(Topic.class), any(LocalDate.class))).thenReturn(Optional.of(testBriefing));

            verify(orchestrationLockService, times(1)).acquireLock(orchestrationDate);
            verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
            verify(orchestrationLockService, never()).releaseLock(orchestrationDate);
            verify(briefingService, never()).getSummariesForBriefing(any(Topic.class), any(LocalDate.class));
            verify(briefingService, never()).generateBriefing(any(Topic.class), any(LocalDate.class), anyList());
        }
    }
}
