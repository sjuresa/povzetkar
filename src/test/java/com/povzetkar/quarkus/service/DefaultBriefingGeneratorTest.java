package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.LockModeType;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@QuarkusTest
class DefaultBriefingGeneratorTest {

    @Inject
    DefaultBriefingGenerator defaultBriefingGenerator;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private MockedStatic<QuarkusTransaction> mockedQuarkusTransaction;

    private Topic testTopic;
    private LocalDate testDate;

    @BeforeEach
    void setUp() {
        Mockito.reset(videoSummaryService);
        PanacheMock.mock(Summary.class);
        PanacheMock.mock(Briefing.class);
        mockedQuarkusTransaction = Mockito.mockStatic(QuarkusTransaction.class);

        testTopic = new Topic();
        testTopic.id = 1L;
        testTopic.name = "Test Topic";
        testDate = LocalDate.now();

        // Mock QuarkusTransaction behavior for call and run
        TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
        mockedQuarkusTransaction.when(QuarkusTransaction::requiringNew).thenReturn(runner);

        when(runner.call(any(Callable.class)))
                .thenAnswer(invocation -> {
                    Callable<?> callable = invocation.getArgument(0);
                    return callable.call();
                });

        doAnswer(invocation -> {
            Runnable runnable = invocation.getArgument(0);
            runnable.run();
            return null;
        }).when(runner).run(any(Runnable.class));
    }

    @Test
    void testGenerateBriefing_noSummaries() {
        // Given
        Mockito.when(Summary.findByTopicAndDateRange(testTopic, testDate))
                .thenReturn(Collections.emptyList());

        // When
        Briefing briefing = defaultBriefingGenerator.generateBriefing(testTopic, testDate);

        // Then
        assertNull(briefing);
        verifyNoInteractions(videoSummaryService);
        PanacheMock.verifyNoInteractions(Briefing.class);
    }

    @Transactional
    void testGenerateBriefing_updateExistingBriefing() {
        // Given
        Summary summary1 = new Summary();
        summary1.summaryText = "Summary 1";
        summary1.id = 1L;
        List<Summary> summaries = List.of(summary1);

        Briefing existingBriefing = new Briefing();
        existingBriefing.id = 10L;
        existingBriefing.metaSummary = "Old Meta-summary";

        Mockito.when(Summary.findByTopicAndDateRange(testTopic, testDate))
                .thenReturn(summaries);
        when(videoSummaryService.createBriefingFromSummaries(anyString(), eq(testDate.toString())))
                .thenReturn("Updated Meta-summary Text");

        PanacheQuery<Briefing> mockPanacheQuery = mock(PanacheQuery.class);
        when(mockPanacheQuery.withLock(any(LockModeType.class))).thenReturn(mockPanacheQuery);
        when(mockPanacheQuery.firstResultOptional()).thenReturn(Optional.of(existingBriefing));

        // Mock the static 'find' method on Briefing
        try (MockedStatic<Briefing> mockedBriefing = mockStatic(Briefing.class)) {
            mockedBriefing.when(() -> Briefing.find(anyString(), any(Object[].class))).thenReturn(mockPanacheQuery);

            // When
            Briefing briefing = defaultBriefingGenerator.generateBriefing(testTopic, testDate);

            // Then
            assertNotNull(briefing);
            assertEquals(existingBriefing.id, briefing.id);
            assertEquals("Updated Meta-summary Text", briefing.metaSummary);
            verify(videoSummaryService, times(1)).createBriefingFromSummaries("Summary 1", testDate.toString());
            /*
            InvalidUseOfMatchersException:
            Invalid use of argument matchers!
            2 matchers expected, 1 recorded:
             */
            PanacheMock.verify(Summary.class, times(1)).list(anyString(), List.of(1L));
        }

    }

    @Transactional
    void testGenerateBriefing_newBriefing() {
        // Given
        Summary summary1 = new Summary();
        summary1.summaryText = "Summary 1";
        summary1.id = 1L;
        List<Summary> summaries = List.of(summary1);

        Mockito.when(Summary.findByTopicAndDateRange(testTopic, testDate))
                .thenReturn(summaries);
        when(videoSummaryService.createBriefingFromSummaries(anyString(), eq(testDate.toString())))
                .thenReturn("Meta-summary Text");

        PanacheQuery<Briefing> mockPanacheQuery = mock(PanacheQuery.class);
        when(mockPanacheQuery.withLock(any(LockModeType.class))).thenReturn(mockPanacheQuery);
        when(mockPanacheQuery.firstResultOptional()).thenReturn(Optional.empty());

        // Mock the static 'find' method on Briefing
        try (MockedStatic<Briefing> mockedBriefing = mockStatic(Briefing.class)) {
            mockedBriefing.when(() -> Briefing.find(anyString(), any(Object[].class))).thenReturn(mockPanacheQuery);

            // When
            Briefing briefing = defaultBriefingGenerator.generateBriefing(testTopic, testDate);

            // Then
            assertNotNull(briefing);
            assertEquals("Meta-summary Text", briefing.metaSummary);
            verify(videoSummaryService, times(1)).createBriefingFromSummaries("Summary 1", testDate.toString());
            mockedBriefing.verify(() -> Briefing.persist(any(Briefing.class)), times(1));
            /*
            InvalidUseOfMatchersException:
            Invalid use of argument matchers!
            2 matchers expected, 1 recorded:
             */
            PanacheMock.verify(Summary.class, times(1)).list(anyString(), List.of(1L));
        }
    }

    @AfterEach
    void tearDown() {
        mockedQuarkusTransaction.close();
    }
}