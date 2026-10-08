package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.*;
import com.povzetkar.quarkus.service.transcript.TranscriptOrchestrator;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.scheduler.FailedExecution;
import io.quarkus.scheduler.ScheduledExecution;
import io.quarkus.scheduler.Scheduler;
import io.quarkus.scheduler.SuccessfulExecution;
import io.quarkus.scheduler.Trigger;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * TC-ING-006 (POV-213): the scheduled orchestration fired by the real Quarkus scheduler, so it runs on the thread the
 * scheduler picks - the POV-197 defect ({@code Uni<Void>} scheduled method on the IO thread) is invisible to a direct
 * call from the test thread. Real lock service, transactions and test DB; external calls mocked as in
 * {@link OrchestrationIntegrationTest}. The scheduler is halted in {@code %test} and fires the job every second
 * while resumed (application.properties); see docs/guides/testing.md.
 */
@QuarkusTest
class ScheduledOrchestrationIntegrationTest {

    /** The job is found by its method - no identity on the production annotation (QA rule 4). */
    private static final String JOB_METHOD = OrchestrationManager.class.getName() + "#runScheduledOrchestration";
    private static final Duration RUN_TIMEOUT = Duration.ofSeconds(30);

    /** Records the finished executions of the orchestration job; delivered on the executing thread after the method. */
    @ApplicationScoped
    static class ScheduledRunRecorder {
        // read through methods only: a field read on the client proxy returns the proxy's own field
        private final List<ScheduledExecution> finished = new CopyOnWriteArrayList<>();

        void onSuccess(@Observes SuccessfulExecution event) {
            addFinished(event.getExecution());
        }

        void onFailure(@Observes FailedExecution event) {
            addFinished(event.getExecution());
        }

        private void addFinished(ScheduledExecution execution) {
            if (isJob(execution)) {
                finished.add(execution);
            }
        }

        private static boolean isJob(ScheduledExecution execution) {
            return JOB_METHOD.equals(execution.getTrigger().getMethodDescription());
        }

        long finishedAfter(Instant instant) {
            return finished.stream().filter(e -> e.getFireTime().isAfter(instant)).count();
        }

        boolean finishedFiredAt(Instant fireTime) {
            return finished.stream().anyMatch(e -> e.getFireTime().equals(fireTime));
        }
    }

    @ConfigProperty(name = "transcript.threshold.minutes")
    int transcriptThresholdMinutes;

    @Inject
    Scheduler scheduler;

    @Inject
    ScheduledRunRecorder recorder;

    @Inject
    MeterRegistry registry;

    @InjectMock
    YouTubeContentFetcherService youTubeContentFetcherService;

    @InjectMock
    TranscriptOrchestrator transcriptOrchestrator;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private Long topicId;
    /** Videos the fetcher returns per date in the current run. */
    private volatile Map<LocalDate, List<YouTubeVideo>> videosByDate = Map.of();
    /** Dates the fetcher received in the current run. */
    private final List<LocalDate> fetchedDates = new CopyOnWriteArrayList<>();
    /** When fireOneScheduledRun last resumed the scheduler; null before the first. */
    private Instant lastResumedAt;

    @BeforeEach
    void setUp() throws IOException {
        QuarkusTransaction.requiringNew().run(() -> {
            deleteTestData();
            topicId = persistTestData();
        });

        when(youTubeContentFetcherService.fetchYouTubeVideos(any(ContentSource.class), any(LocalDate.class), any(), any()))
                .thenAnswer(invocation -> {
                    LocalDate date = invocation.getArgument(1);
                    fetchedDates.add(date);
                    return videosByDate.getOrDefault(date, List.of());
                });
        when(transcriptOrchestrator.fetch(anyString(), any(ProcessingContext.class))).thenReturn("Transcript");
        when(videoSummaryService.acquireClient()).thenReturn(mock(GenAiClient.class));
        when(videoSummaryService.generateContent(any(GenAiClient.class), any(com.google.genai.types.Content.class), anyString(), anyString(), anyString()))
                .thenReturn("Generated Summary");
        when(videoSummaryService.createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString(), any()))
                .thenReturn("Generated Meta-summary");
    }

    @AfterEach
    void tearDown() {
        scheduler.pause();
        // undo the pause by id of the other jobs (symmetric); nothing fires while the scheduler is paused
        otherJobIds().forEach(scheduler::resume);
        // pause() does not stop an execution in flight; the gauge misses a run fired but not yet in the method, so
        // wait for the finished event of the last firing first
        Instant lastFire = orchestrationTrigger().getPreviousFireTime();
        if (lastResumedAt != null && lastFire != null && lastFire.isAfter(lastResumedAt)) {
            await().atMost(RUN_TIMEOUT).until(() -> recorder.finishedFiredAt(lastFire));
        }
        await().atMost(RUN_TIMEOUT).until(() -> activeOrchestrations() == 0);
        QuarkusTransaction.requiringNew().run(this::deleteTestData);
    }

    @Test
    void scheduledRun_fetchesAndSummarizesYesterdayAndToday_andGeneratesYesterdaysBriefingIdempotently() throws IOException {
        LocalDate today = LocalDate.now();
        LocalDate yesterday = today.minusDays(1);
        videosByDate = Map.of(
                yesterday, List.of(longVideo("yesterday_video", yesterday.atTime(LocalTime.NOON).atZone(ZoneId.systemDefault()).toInstant())),
                today, List.of(longVideo("today_video", Instant.now())));
        double failuresBefore = scheduledFailures();

        fireOneScheduledRun();

        assumeSameDay(today);
        QuarkusTransaction.requiringNew().run(() -> {
            List<ContentItem> items = ContentItem.listAll();
            assertEquals(2, items.size());
            assertTrue(items.stream().allMatch(item -> item.processingStatus == ProcessingStatus.COMPLETED),
                    () -> "statuses: " + items.stream().map(item -> item.processingStatus).toList());
            assertEquals(2, Summary.count());
            assertTrue(Briefing.findByTopicAndDate(topicId, yesterday).isPresent(), "yesterday's briefing");
            assertTrue(Briefing.findByTopicAndDate(topicId, today).isEmpty(), "no briefing for today");
            assertEquals(0, OrchestrationLock.count(), "lock released");
        });
        assertEquals(Set.of(yesterday, today), Set.copyOf(fetchedDates), "fetched dates");
        assertEquals(failuresBefore, scheduledFailures(), "povzetkar_orchestration_failed_total{trigger=scheduled}");

        // Second run: nothing new; it must really run (fetcher called again) and leave exactly one briefing
        videosByDate = Map.of();
        fireOneScheduledRun();

        assumeSameDay(today);
        verify(youTubeContentFetcherService, times(2)).fetchYouTubeVideos(any(ContentSource.class), eq(yesterday), any(), any());
        // the briefing is up to date - not regenerated (no second Gemini call)
        verify(videoSummaryService, times(1)).createBriefingFromSummaries(any(GenAiClient.class), anyString(), anyString(), anyString(), any());
        QuarkusTransaction.requiringNew().run(() -> {
            assertEquals(1, Briefing.count());
            assertTrue(Briefing.findByTopicAndDate(topicId, yesterday).isPresent(), "yesterday's briefing");
            assertEquals(0, OrchestrationLock.count(), "lock released");
        });
        assertEquals(failuresBefore, scheduledFailures(), "povzetkar_orchestration_failed_total{trigger=scheduled}");
    }

    @Test
    void allScheduledJobsHaveASchedule() {
        List<Trigger> jobs = scheduler.getScheduledJobs();
        assertEquals(Set.of(
                        JOB_METHOD,
                        ContentSourceDetailService.class.getName() + "#scheduledRefreshAllContentSources",
                        RetentionService.class.getName() + "#purgeOldAuditLogs",
                        IdempotencyCleanupTask.class.getName() + "#cleanupOldIdempotencyRequests"),
                jobs.stream().map(Trigger::getMethodDescription).collect(Collectors.toSet()));
        // the ${...} crons of every job (incl. retention and enrichment) parse - without asserting their values
        jobs.forEach(job -> assertNotNull(job.getNextFireTime(), job.getId()));
    }

    /**
     * Lets the scheduler fire the orchestration job exactly once and waits until that execution has finished. Only the
     * orchestration runs: every other job is paused by id before the scheduler is resumed (resume(id) alone does not
     * fire a job while the scheduler is halted). The scheduler is paused right after the first firing - the next one
     * is a second away - so no second run starts (two overlapping runs would split the dates between them, because
     * the lock is per date, and both would generate yesterday's briefing).
     */
    private void fireOneScheduledRun() {
        fetchedDates.clear();
        otherJobIds().forEach(scheduler::pause);
        Instant resumedAt = Instant.now();
        lastResumedAt = resumedAt;
        scheduler.resume();
        await().atMost(RUN_TIMEOUT).pollDelay(Duration.ZERO).pollInterval(Duration.ofMillis(10))
                .until(() -> firedAfter(resumedAt));
        scheduler.pause();
        await().atMost(RUN_TIMEOUT).until(() -> recorder.finishedAfter(resumedAt) > 0);
        await().atMost(RUN_TIMEOUT).until(() -> activeOrchestrations() == 0);
        assertEquals(1, recorder.finishedAfter(resumedAt), "exactly one scheduled run");
    }

    private boolean firedAfter(Instant instant) {
        Instant previousFire = orchestrationTrigger().getPreviousFireTime();
        return previousFire != null && previousFire.isAfter(instant);
    }

    private Trigger orchestrationTrigger() {
        return scheduler.getScheduledJobs().stream()
                .filter(job -> JOB_METHOD.equals(job.getMethodDescription()))
                .findFirst()
                .orElseThrow();
    }

    private List<String> otherJobIds() {
        return scheduler.getScheduledJobs().stream()
                .filter(job -> !JOB_METHOD.equals(job.getMethodDescription()))
                .map(Trigger::getId)
                .toList();
    }

    /**
     * A run that crossed midnight works on other dates than the test computed - not a failure of the code. Checked by
     * the clock, not by the fetched dates, so a run fetching wrong dates fails instead of being skipped.
     */
    private void assumeSameDay(LocalDate today) {
        assumeTrue(LocalDate.now().equals(today), "run crossed midnight");
    }

    private YouTubeVideo longVideo(String videoId, Instant publishedAt) {
        return new YouTubeVideo(videoId, videoId + " title", "http://youtube.com/watch?v=" + videoId,
                "Author", "scheduled_test_channel_id", publishedAt, Duration.ofMinutes(transcriptThresholdMinutes + 1));
    }

    private double scheduledFailures() {
        Counter counter = registry.find("povzetkar_orchestration_failed_total")
                .tags("trigger", "scheduled", "topic_id", "all").counter();
        return counter == null ? 0 : counter.count();
    }

    private double activeOrchestrations() {
        Gauge gauge = registry.find("povzetkar_active_orchestrations_current").gauge();
        return gauge == null ? 0 : gauge.value();
    }

    private Long persistTestData() {
        User user = UserBuilder.aUser()
                .withUsername("scheduled_orchestration_user")
                .withEmail("scheduled_orchestration_user@example.com")
                .withName("Scheduled Test User")
                .build();
        user.persist();

        Topic topic = TopicBuilder.aTopic()
                .withName("Scheduled Topic")
                .withUser(user)
                .build();
        topic.persist();

        ContentSource source = ContentSourceBuilder.aContentSource()
                .withSourceIdentifier("scheduled_test_channel_id")
                .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
                .withName("Scheduled Test Channel")
                .withSubscriberCount(1000)
                .build();
        source.persist();

        topic.contentSources.add(source);
        return topic.id;
    }

    private void deleteTestData() {
        Briefing.deleteAll();
        Summary.deleteAll();
        ContentItem.deleteAll();
        Topic.deleteAll();
        ContentSource.deleteAll();
        User.deleteAll();
        OrchestrationLock.deleteAll();
    }
}
