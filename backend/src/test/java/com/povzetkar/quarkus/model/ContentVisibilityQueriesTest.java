package com.povzetkar.quarkus.model;

import com.povzetkar.quarkus.builder.BriefingBuilder;
import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import io.quarkus.hibernate.orm.panache.PanacheQuery;
import io.quarkus.panache.common.Sort;
import io.quarkus.test.TestTransaction;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The per-user visibility queries of {@link ContentSource}, {@link ContentItem}, {@link Summary} and the
 * topic paging query, against the real database (QA rule 3) — POV-219. The resources that use them are
 * tested with these methods mocked.
 * <p>
 * Fixture: Alice owns an enabled and a disabled topic, Bob owns one topic. Other data in the test database
 * (e.g. the demo seed) is only ever asserted as absent from Alice's results or present in the unrestricted ones.
 */
@QuarkusTest
class ContentVisibilityQueriesTest {

    private static final Sort ITEM_ORDER = Sort.by("c.publishedAt", Sort.Direction.Descending).and("c.id", Sort.Direction.Descending);

    @Inject
    EntityManager em;

    /** Sources and items named by who can see them. */
    private record Fixture(User alice, User bob, Topic aliceTopic, Topic aliceDisabledTopic, Topic bobTopic,
                           ContentSource shared, ContentSource aliceTwice, ContentSource inDisabled,
                           ContentSource bobOnly, ContentSource orphan,
                           ContentItem sharedItem, ContentItem aliceItem, ContentItem bobItem,
                           ContentItem briefedBobItem, ContentItem orphanItem) {
        ContentScope aliceScope() {
            return ContentScope.of(alice);
        }
    }

    private Fixture fixture() {
        User alice = persist(UserBuilder.aUser().build());
        User bob = persist(UserBuilder.aUser().build());

        ContentSource shared = persist(ContentSourceBuilder.aContentSource().withName("Shared Channel").build());
        ContentSource aliceTwice = persist(ContentSourceBuilder.aContentSource().withName("Alice Channel").build());
        ContentSource inDisabled = persist(ContentSourceBuilder.aContentSource().withName("Paused Channel").build());
        ContentSource bobOnly = persist(ContentSourceBuilder.aContentSource().withName("Bob Channel").withDescription("bob secret").build());
        ContentSource orphan = persist(ContentSourceBuilder.aContentSource().withName("Orphan Channel").build());

        Topic aliceTopic = topic(alice, "Alice Topic", false, shared, aliceTwice);
        Topic aliceSecond = topic(alice, "Alice Second Topic", false, aliceTwice);
        Topic aliceDisabled = topic(alice, "Alice Paused Topic", true, inDisabled);
        Topic bobTopic = topic(bob, "Bob Topic", false, shared, bobOnly);
        assertThat(aliceSecond.id).isNotNull();

        ContentItem sharedItem = item(shared, ProcessingStatus.COMPLETED);
        ContentItem aliceItem = item(aliceTwice, ProcessingStatus.PENDING);
        ContentItem bobItem = item(bobOnly, ProcessingStatus.COMPLETED);
        ContentItem briefedBobItem = item(bobOnly, ProcessingStatus.COMPLETED);
        ContentItem orphanItem = item(orphan, ProcessingStatus.COMPLETED);

        persist(SummaryBuilder.aSummary().withContentItem(sharedItem).withBriefingDate(LocalDate.now()).build());
        Summary briefedSummary = persist(SummaryBuilder.aSummary().withContentItem(briefedBobItem).withBriefingDate(LocalDate.now()).build());
        persist(SummaryBuilder.aSummary().withContentItem(bobItem).withBriefingDate(LocalDate.now()).build());
        // Bob Channel was once in Alice's topic: her briefing still holds one of its summaries
        persist(BriefingBuilder.aBriefing().withTopic(aliceTopic).withSummaries(new java.util.HashSet<>(Set.of(briefedSummary))).build());

        em.flush();
        em.clear();
        return new Fixture(alice, bob, aliceTopic, aliceDisabled, bobTopic, shared, aliceTwice, inDisabled, bobOnly, orphan,
                sharedItem, aliceItem, bobItem, briefedBobItem, orphanItem);
    }

    // --- ContentSource ---

    @Test
    @TestTransaction
    void sourcesAreVisibleThroughOwnTopicsIncludingDisabledOnesAndListedOnce() {
        Fixture f = fixture();

        PanacheQuery<ContentSource> query = ContentSource.findVisible(f.aliceScope(), null, null);

        assertThat(ids(query.list())).containsExactly(f.shared().id, f.aliceTwice().id, f.inDisabled().id);
        assertThat(query.count()).as("a source in two of Alice's topics is counted once").isEqualTo(3);
    }

    @Test
    @TestTransaction
    void searchAndExclusionNeverReachForeignSources() {
        Fixture f = fixture();

        assertThat(ContentSource.findVisible(f.aliceScope(), "bob", null).list()).isEmpty();
        assertThat(ids(ContentSource.findVisible(f.aliceScope(), "CHANNEL", List.of(f.shared().id)).list()))
                .containsExactly(f.aliceTwice().id, f.inDisabled().id);
        assertThat(ids(ContentSource.findVisible(ContentScope.of(f.bob()), "secret", null).list()))
                .containsExactly(f.bobOnly().id);
    }

    @Test
    @TestTransaction
    void unrestrictedScopeSeesEverySource() {
        Fixture f = fixture();

        assertThat(ids(ContentSource.findVisible(ContentScope.unrestricted(), null, null).list()))
                .contains(f.shared().id, f.aliceTwice().id, f.inDisabled().id, f.bobOnly().id, f.orphan().id);
    }

    @Test
    @TestTransaction
    void findVisibleByIdHidesForeignSources() {
        Fixture f = fixture();

        assertThat(ContentSource.findVisibleById(f.inDisabled().id, f.aliceScope())).isPresent();
        assertThat(ContentSource.findVisibleById(f.bobOnly().id, f.aliceScope())).isEmpty();
        assertThat(ContentSource.findVisibleById(f.orphan().id, f.aliceScope())).isEmpty();
        assertThat(ContentSource.findVisibleById(f.orphan().id, ContentScope.unrestricted())).isPresent();
    }

    @Test
    @TestTransaction
    void listVisibleByIdsDropsForeignAndUnknownIds() {
        Fixture f = fixture();

        assertThat(ids(ContentSource.listVisibleByIds(Set.of(f.aliceTwice().id, f.bobOnly().id, -1L), f.aliceScope())))
                .containsExactly(f.aliceTwice().id);
        assertThat(ids(ContentSource.listVisibleByIds(Set.of(f.aliceTwice().id, f.bobOnly().id), ContentScope.unrestricted())))
                .containsExactlyInAnyOrder(f.aliceTwice().id, f.bobOnly().id);
        assertThat(ContentSource.listVisibleByIds(Set.of(), f.aliceScope())).isEmpty();
    }

    @Test
    @TestTransaction
    void findByIdentifierMatchesIdentifierAndType() {
        Fixture f = fixture();

        assertThat(ContentSource.findByIdentifier(f.bobOnly().sourceIdentifier, SourceTypeEnum.YOUTUBE.getId()))
                .map(s -> s.id).contains(f.bobOnly().id);
        assertThat(ContentSource.findByIdentifier(f.bobOnly().sourceIdentifier, SourceTypeEnum.BLOG.getId())).isEmpty();
    }

    // --- ContentItem ---

    @Test
    @TestTransaction
    void itemsAreVisibleThroughOwnTopicsOrOwnBriefings() {
        Fixture f = fixture();

        PanacheQuery<Long> query = ContentItem.findVisibleIds(f.aliceScope(), new ContentItem.Filter(null, null, null), ITEM_ORDER);

        assertThat(query.list()).containsExactlyInAnyOrder(f.sharedItem().id, f.aliceItem().id, f.briefedBobItem().id);
        assertThat(query.count()).isEqualTo(3);
    }

    @Test
    @TestTransaction
    void itemFiltersCombineWithVisibility() {
        Fixture f = fixture();

        assertThat(ContentItem.findVisibleIds(f.aliceScope(), new ContentItem.Filter(f.bobOnly().id, null, null), ITEM_ORDER).list())
                .as("a removed source: only its briefed item stays visible").containsExactly(f.briefedBobItem().id);
        assertThat(ContentItem.findVisibleIds(f.aliceScope(), new ContentItem.Filter(null, List.of(ProcessingStatus.PENDING), null), ITEM_ORDER).list())
                .containsExactly(f.aliceItem().id);
        assertThat(ContentItem.findVisibleIds(f.aliceScope(), new ContentItem.Filter(null, null, f.aliceTopic().id), ITEM_ORDER).list())
                .containsExactlyInAnyOrder(f.sharedItem().id, f.aliceItem().id);
        assertThat(ContentItem.findVisibleIds(ContentScope.unrestricted(), new ContentItem.Filter(f.orphan().id, null, null), ITEM_ORDER).list())
                .containsExactly(f.orphanItem().id);
    }

    @Test
    @TestTransaction
    void findVisibleItemByIdHonoursTopicsAndBriefings() {
        Fixture f = fixture();

        assertThat(ContentItem.findVisibleByIdWithAssociations(f.aliceItem().id, f.aliceScope())).isPresent();
        assertThat(ContentItem.findVisibleByIdWithAssociations(f.briefedBobItem().id, f.aliceScope())).isPresent();
        assertThat(ContentItem.findVisibleByIdWithAssociations(f.bobItem().id, f.aliceScope())).isEmpty();
        assertThat(ContentItem.findVisibleByIdWithAssociations(f.orphanItem().id, f.aliceScope())).isEmpty();
        assertThat(ContentItem.findVisibleByIdWithAssociations(f.orphanItem().id, ContentScope.unrestricted())).isPresent();
    }

    /**
     * Moved from ContentItemApiResourceTest: a source in several topics makes the collection fetch return
     * several rows for one item; the lookup must still resolve to that single item (Hibernate 7.4).
     */
    @Test
    @TestTransaction
    void itemWhoseSourceIsInSeveralTopicsResolvesToOneItem() {
        Fixture f = fixture();

        ContentItem restricted = ContentItem.findVisibleByIdWithAssociations(f.aliceItem().id, f.aliceScope()).orElseThrow();
        ContentItem unrestricted = ContentItem.findByIdWithAssociations(f.aliceItem().id).orElseThrow();

        assertThat(restricted.id).isEqualTo(f.aliceItem().id);
        assertThat(restricted.contentSource.topics).hasSize(2);
        assertThat(unrestricted.contentSource.topics).hasSize(2);
    }

    /**
     * Moved from ContentItemApiResourceTest: items sharing the sort value must neither repeat nor vanish across
     * pages — the id tiebreaker makes the order total (id descending among equals).
     */
    @Test
    @TestTransaction
    void pagingWithEqualPublishedAtIsStable() {
        Fixture f = fixture();
        ContentItem second = item(em.find(ContentSource.class, f.aliceTwice().id), ProcessingStatus.PENDING);
        ContentItem third = item(em.find(ContentSource.class, f.aliceTwice().id), ProcessingStatus.PENDING);
        ContentItem.update("publishedAt = ?1 where contentSource.id = ?2", Instant.parse("2026-01-01T00:00:00Z"), f.aliceTwice().id);
        em.clear();

        ContentItem.Filter bySource = new ContentItem.Filter(f.aliceTwice().id, null, null);
        List<Long> seen = new ArrayList<>();
        for (int page = 0; page < 3; page++) {
            seen.addAll(ContentItem.findVisibleIds(f.aliceScope(), bySource, ITEM_ORDER).page(page, 1).list());
        }

        assertThat(seen).containsExactly(third.id, second.id, f.aliceItem().id);
    }

    /** Moved from ContentItemApiResourceTest: the sort is applied to the fetch-join load, where id is ambiguous. */
    @Test
    @TestTransaction
    void loadingAPageKeepsTheRequestedOrder() {
        Fixture f = fixture();
        Sort byIdDesc = Sort.by("c.id", Sort.Direction.Descending);

        List<ContentItem> page = ContentItem.listByIdsWithAssociations(
                List.of(f.sharedItem().id, f.aliceItem().id, f.briefedBobItem().id), byIdDesc);

        assertThat(page).extracting(i -> i.id)
                .containsExactly(f.briefedBobItem().id, f.aliceItem().id, f.sharedItem().id);
    }

    // --- Summary ---

    @Test
    @TestTransaction
    void summaryIsVisibleWithItsItem() {
        Fixture f = fixture();

        assertThat(Summary.findVisibleByContentIdWithAssociations(f.sharedItem().id, f.aliceScope())).isPresent();
        assertThat(Summary.findVisibleByContentIdWithAssociations(f.briefedBobItem().id, f.aliceScope())).isPresent();
        assertThat(Summary.findVisibleByContentIdWithAssociations(f.bobItem().id, f.aliceScope())).isEmpty();
        assertThat(Summary.findVisibleByContentIdWithAssociations(f.bobItem().id, ContentScope.of(f.bob()))).isPresent();
        assertThat(Summary.findVisibleByContentIdWithAssociations(f.bobItem().id, ContentScope.unrestricted())).isPresent();
    }

    @Test
    @TestTransaction
    void findContentItemIdsWithSummaryReturnsOnlySummarisedItems() {
        Fixture f = fixture();

        assertThat(Summary.findContentItemIdsWithSummary(List.of(f.sharedItem().id, f.aliceItem().id, f.bobItem().id)))
                .containsExactlyInAnyOrder(f.sharedItem().id, f.bobItem().id);
        assertThat(Summary.findContentItemIdsWithSummary(List.of())).isEmpty();
    }

    // --- Topic ---

    /**
     * Moved from TopicApiResourceTest: every page must hold whole topics (limit per topic, not per joined row)
     * with all their sources — guards the Hibernate 7.4 change that moves the collection-fetch limit into SQL.
     */
    @Test
    @TestTransaction
    void topicsArePagedWholeWithAllSources() {
        Fixture f = fixture();
        User alice = em.find(User.class, f.alice().id);
        Sort byId = Sort.by("t.id");

        List<Topic> first = Topic.findByUserWithSources(alice, byId).page(0, 2).list();
        List<Topic> second = Topic.findByUserWithSources(alice, byId).page(1, 2).list();

        assertThat(Topic.findByUserWithSources(alice, byId).count()).isEqualTo(3);
        assertThat(first).extracting(t -> t.name).containsExactly("Alice Topic", "Alice Second Topic");
        assertThat(first.get(0).contentSources).hasSize(2);
        assertThat(first.get(1).contentSources).hasSize(1);
        assertThat(second).extracting(t -> t.name).containsExactly("Alice Paused Topic");
    }

    @Test
    @TestTransaction
    void topicLookupsAreBoundToTheirUser() {
        Fixture f = fixture();
        User alice = em.find(User.class, f.alice().id);

        assertThat(Topic.findByIdAndUserWithSources(f.aliceTopic().id, alice)).isPresent();
        assertThat(Topic.findByIdAndUserWithSources(f.bobTopic().id, alice)).isEmpty();
        assertThat(Topic.findByUserAndName(alice, "Alice Topic")).isPresent();
        assertThat(Topic.findByUserAndName(alice, "Bob Topic")).isEmpty();
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

    private ContentItem item(ContentSource source, ProcessingStatus status) {
        return persist(ContentItemBuilder.aContentItem().withContentSource(source).withProcessingStatus(status).build());
    }

    private static List<Long> ids(List<ContentSource> sources) {
        return sources.stream().map(s -> s.id).toList();
    }
}
