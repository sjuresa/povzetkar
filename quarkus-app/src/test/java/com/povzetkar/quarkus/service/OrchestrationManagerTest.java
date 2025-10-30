package com.povzetkar.quarkus.service;

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
import org.mockito.Mockito;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

    @BeforeEach
    void setUp() {
        Mockito.reset(orchestrationLockService, topicProcessor, briefingService, managedExecutor);
        PanacheMock.mock(User.class);
        PanacheMock.mock(Topic.class);

        // Mock the executor to run tasks immediately on the same thread
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
    }

    private void setupCommonMocks(LocalDate orchestrationDate) {
        Mockito.when(User.listAllActive()).thenReturn(Collections.singletonList(testUser));
        Mockito.when(Topic.findByUsers(any(List.class))).thenReturn(Collections.singletonList(testTopic));
        Mockito.when(Topic.listAll()).thenReturn(Collections.singletonList(testTopic));
        doNothing().when(orchestrationLockService).acquireLock(orchestrationDate);
        doNothing().when(orchestrationLockService).releaseLock(orchestrationDate);
        when(topicProcessor.processTopicUni(any(Topic.class), any(LocalDate.class)))
                .thenReturn(Uni.createFrom().voidItem());
        when(briefingService.generateBriefing(any(Topic.class), any(LocalDate.class))).thenReturn(null);
    }

    @Test
    void testRunOrchestrationManually_singlePastDate() {
        // Given
        LocalDate pastDate = LocalDate.now().minusDays(5);
        setupCommonMocks(pastDate);

        // When
        orchestrationManager.runOrchestrationManually(pastDate, null).await().indefinitely();

        // Then
        verify(orchestrationLockService, times(1)).acquireLock(pastDate);
        verify(topicProcessor, times(1)).processTopicUni(testTopic, pastDate);
        verify(orchestrationLockService, times(1)).releaseLock(pastDate);
        verify(briefingService, times(1)).generateBriefing(testTopic, pastDate);
    }

    @Test
    void testRunOrchestrationManually_dateRange() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(3);
        LocalDate endDate = LocalDate.now().minusDays(1);
        long numOfDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        setupCommonMocks(startDate);
        setupCommonMocks(endDate);

        // When
        orchestrationManager.runOrchestrationManually(startDate, endDate).await().indefinitely();

        // Then
        verify(orchestrationLockService, times((int) numOfDays)).acquireLock(any(LocalDate.class));
        verify(topicProcessor, times((int) numOfDays)).processTopicUni(eq(testTopic), any(LocalDate.class));
        verify(orchestrationLockService, times((int) numOfDays)).releaseLock(any(LocalDate.class));
        verify(briefingService, times((int) numOfDays)).generateBriefing(eq(testTopic), any(LocalDate.class));
    }

    @Test
    void testRunOrchestrationManually_futureDate_throwsException() {
        // Given
        LocalDate futureDate = LocalDate.now().plusDays(1);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(futureDate, null));
    }

    @Test
    void testRunOrchestrationManually_startDateAfterEndDate_throwsException() {
        // Given
        LocalDate startDate = LocalDate.now().minusDays(1);
        LocalDate endDate = LocalDate.now().minusDays(5);

        // When / Then
        assertThrows(IllegalArgumentException.class, () -> orchestrationManager.runOrchestrationManually(startDate, endDate));
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
        verify(orchestrationLockService, times(1)).acquireLock(orchestrationDate);
        verify(topicProcessor, never()).processTopicUni(any(Topic.class), any(LocalDate.class));
        verify(orchestrationLockService, never()).releaseLock(orchestrationDate);
        verify(briefingService, never()).generateBriefing(any(Topic.class), any(LocalDate.class));
    }
}
