package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.model.*;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.narayana.jta.TransactionRunnerOptions;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class BriefingServiceTest {

    @Inject
    BriefingService briefingService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    @BeforeEach
    void setUp() {
        reset(videoSummaryService);
        PanacheMock.mock(Summary.class);
    }

    private TransactionRunnerOptions setupMockTransactionRunnerOptions() {
        TransactionRunnerOptions runner = mock(TransactionRunnerOptions.class);
        when(runner.call(any(Callable.class)))
                .thenAnswer(i -> ((Callable<?>) i.getArgument(0)).call());
        return runner;
    }

    @Test
    void testGenerateBriefing_CreatesAndPersistsBriefing() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedConstruction<Briefing> cons = mockConstruction(Briefing.class, (mock, ctx) -> doNothing().when(mock).persist())) {

            TransactionRunnerOptions runner = setupMockTransactionRunnerOptions();
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);

            // Given
            User user = new User();
            user.username = "test_user_" + UUID.randomUUID();
            user.name = "Test User";

            Topic topic = new Topic();
            topic.name = "Test Topic";
            topic.user = user;

            ContentItem contentItem = new ContentItem();
            contentItem.sourceUrl = "http://example.com/" + UUID.randomUUID();
            contentItem.rawContent = "raw content";
            contentItem.publishedAt = Instant.now();

            Summary summary = new Summary("Summary text 1.", contentItem);
            summary.id = 1L;
            List<Summary> summaries = List.of(summary);

            LocalDate briefingDate = LocalDate.now();

            when(Summary.findByTopicAndDateRange(topic, briefingDate)).thenReturn(summaries);
            when(Summary.list("id in ?1", List.of(1L))).thenReturn((List<PanacheEntityBase>) (List<?>) summaries);
            when(videoSummaryService.createBriefingFromSummaries(any(), any())).thenReturn("Meta-summary from GenAI");

            // When
            Briefing briefing = briefingService.generateBriefing(topic, briefingDate);

            // Then
            assertNotNull(briefing);
            assertEquals(1, cons.constructed().size());
            verify(cons.constructed().getFirst()).persist();
        }
    }

    @Test
    void testGenerateBriefing_HandlesDuplicateGracefully() {
        try (MockedStatic<QuarkusTransaction> tx = mockStatic(QuarkusTransaction.class);
             MockedConstruction<Briefing> cons = mockConstruction(Briefing.class, (mock, ctx) -> doThrow(new PersistenceException("Duplicate entry")).when(mock).persist())) {

            TransactionRunnerOptions runner = setupMockTransactionRunnerOptions();
            tx.when(QuarkusTransaction::requiringNew).thenReturn(runner);

            // Given
            User user = new User();
            user.username = "test_user_" + UUID.randomUUID();
            user.name = "Test User";

            Topic topic = new Topic();
            topic.name = "Test Topic";
            topic.user = user;

            LocalDate briefingDate = LocalDate.now();

            List<Summary> summaries = List.of(new Summary());
            when(Summary.findByTopicAndDateRange(any(), any())).thenReturn(summaries);
            // Explicitly use the List<Object...> version
            when(Summary.list("id in ?1", List.of(1L))).thenReturn((List<PanacheEntityBase>) (List<?>) summaries);
            when(videoSummaryService.createBriefingFromSummaries(any(), any())).thenReturn("A new meta summary");

            // When
            Briefing newBriefing = briefingService.generateBriefing(topic, briefingDate);

            // Then
            assertNull(newBriefing);
            assertEquals(1, cons.constructed().size());
            verify(cons.constructed().getFirst()).persist();
        }
    }
}