package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.builder.YouTubeVideoBuilder;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The transcript retry state of {@link YouTubeVideo} (V38) and the selection of items due for (re)processing,
 * against the real database (QA rule 3) — POV-222. Other tests mock the entity and these queries.
 */
@QuarkusTest
class TranscriptRetryStatePersistenceTest {

    @Inject
    EntityManager em;

    @Test
    @TestTransaction
    void retryStateRoundTrips() {
        Instant next = Instant.now().plus(30, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MICROS);
        YouTubeVideo video = video();
        video.transcriptAttempts = 3;
        video.nextTranscriptAttemptAt = next;
        em.persist(video);
        em.flush();
        em.clear();

        YouTubeVideo loaded = YouTubeVideo.findById(video.id);

        assertThat(loaded.transcriptAttempts).isEqualTo(3);
        assertThat(loaded.nextTranscriptAttemptAt).isEqualTo(next);
    }

    @Test
    @TestTransaction
    void newVideoHasNoRetryState() {
        YouTubeVideo video = video();
        em.persist(video);
        em.flush();
        em.clear();

        YouTubeVideo loaded = YouTubeVideo.findById(video.id);

        assertThat(loaded.transcriptAttempts).isNull();
        assertThat(loaded.nextTranscriptAttemptAt).isNull();
    }

    // --- selection of items due for (re)processing (OrchestrationManager / TopicProcessor) ---

    private static final LocalDate DAY = LocalDate.of(2020, 1, 15);
    private static final Instant NOW = Instant.parse("2026-10-02T09:27:02Z");

    private record Fixture(Topic enabledTopic, Topic otherTopic, Topic disabledTopic,
                           YouTubeVideo due, YouTubeVideo notDue, YouTubeVideo dueExactlyNow, YouTubeVideo pending,
                           ContentItem plainRetry, YouTubeVideo completed, YouTubeVideo otherDay,
                           YouTubeVideo inOtherTopic, YouTubeVideo inDisabledTopic) {
    }

    private Fixture fixture() {
        User user = persist(UserBuilder.aUser().build());
        ContentSource source = persist(ContentSourceBuilder.aContentSource().build());
        ContentSource otherSource = persist(ContentSourceBuilder.aContentSource().build());
        ContentSource disabledSource = persist(ContentSourceBuilder.aContentSource().build());
        Topic enabledTopic = topic(user, "Retry Topic", false, source);
        Topic otherTopic = topic(user, "Retry Other Topic", false, otherSource);
        Topic disabledTopic = topic(user, "Retry Paused Topic", true, disabledSource);

        Instant onDay = DAY.atTime(10, 0).toInstant(ZoneOffset.UTC);
        Fixture f = new Fixture(enabledTopic, otherTopic, disabledTopic,
                video(source, ProcessingStatus.RETRY_LATER, onDay, NOW.minusSeconds(60)),
                video(source, ProcessingStatus.RETRY_LATER, onDay, NOW.plusSeconds(600)),
                video(source, ProcessingStatus.RETRY_LATER, onDay, NOW),
                video(source, ProcessingStatus.PENDING, onDay, null),
                persist(ContentItemBuilder.aContentItem().withContentSource(source)
                        .withProcessingStatus(ProcessingStatus.RETRY_LATER).withPublishedAt(onDay).build()),
                video(source, ProcessingStatus.COMPLETED, onDay, null),
                video(source, ProcessingStatus.PENDING, onDay.plus(1, ChronoUnit.DAYS), null),
                video(otherSource, ProcessingStatus.PENDING, onDay, null),
                video(disabledSource, ProcessingStatus.PENDING, onDay, null));
        em.flush();
        em.clear();
        return f;
    }

    @Test
    @TestTransaction
    void dueInEnabledTopicsSelectsDueAndNeverFailedItemsOfTheDay() {
        Fixture f = fixture();

        List<Long> ids = ids(ContentItem.findDueInEnabledTopics(DAY, null, NOW, 0L).list());

        assertThat(ids).contains(f.due().id, f.dueExactlyNow().id, f.pending().id, f.plainRetry().id, f.inOtherTopic().id)
                .doesNotContain(f.notDue().id, f.completed().id, f.otherDay().id, f.inDisabledTopic().id)
                .doesNotHaveDuplicates();
    }

    @Test
    @TestTransaction
    void dueInEnabledTopicsPagesByIdAfterTheLastSeenItem() {
        Fixture f = fixture();
        List<Long> all = ids(ContentItem.findDueInEnabledTopics(DAY, f.enabledTopic().id, NOW, 0L).list());

        List<Long> afterFirst = ids(ContentItem.findDueInEnabledTopics(DAY, f.enabledTopic().id, NOW, all.getFirst()).list());

        assertThat(all).isSorted().hasSize(4);
        assertThat(afterFirst).containsExactlyElementsOf(all.subList(1, all.size()));
    }

    @Test
    @TestTransaction
    void dueInEnabledTopicsCanBeLimitedToOneTopic() {
        Fixture f = fixture();

        List<Long> ids = ids(ContentItem.findDueInEnabledTopics(DAY, f.enabledTopic().id, NOW, 0L).list());

        assertThat(ids).containsExactlyInAnyOrder(f.due().id, f.dueExactlyNow().id, f.pending().id, f.plainRetry().id);
    }

    @Test
    @TestTransaction
    void dueInTopicSelectsDueItemsOfThatTopic() {
        Fixture f = fixture();

        assertThat(ids(ContentItem.listDueInTopic(f.enabledTopic().id, DAY, NOW)))
                .containsExactlyInAnyOrder(f.due().id, f.dueExactlyNow().id, f.pending().id, f.plainRetry().id);
        assertThat(ids(ContentItem.listDueInTopic(f.disabledTopic().id, DAY, NOW)))
                .as("unchanged: the per-topic selection does not filter on the topic being enabled")
                .containsExactly(f.inDisabledTopic().id);
    }

    private <T> T persist(T entity) {
        em.persist(entity);
        return entity;
    }

    private Topic topic(User user, String name, boolean disabled, ContentSource... sources) {
        TopicBuilder builder = TopicBuilder.aTopic().withName(name).withUser(user);
        Topic topic = (disabled ? builder.disabled() : builder).build();
        topic.contentSources.addAll(List.of(sources));
        return persist(topic);
    }

    private YouTubeVideo video(ContentSource source, ProcessingStatus status, Instant publishedAt, Instant nextAttemptAt) {
        YouTubeVideo video = YouTubeVideoBuilder.aYouTubeVideo()
                .withVideoId("retry-" + UUID.randomUUID())
                .withContentSource(source)
                .withProcessingStatus(status)
                .withPublishedAt(publishedAt)
                .build();
        video.nextTranscriptAttemptAt = nextAttemptAt;
        return persist(video);
    }

    private static List<Long> ids(List<ContentItem> items) {
        return items.stream().map(item -> item.id).toList();
    }

    private YouTubeVideo video() {
        ContentSource source = ContentSourceBuilder.aContentSource().build();
        em.persist(source);
        return YouTubeVideoBuilder.aYouTubeVideo()
                .withVideoId("retry-" + UUID.randomUUID())
                .withContentSource(source)
                .withProcessingStatus(ProcessingStatus.RETRY_LATER)
                .build();
    }
}
