package com.povzetkar.quarkus.api.mapper;

import com.povzetkar.quarkus.builder.BriefingBuilder;
import com.povzetkar.quarkus.builder.ContentItemBuilder;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.SummaryBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.Briefing;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentScope;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An item's {@code topicName} comes only from topics in the viewer's scope: a source can be in other users'
 * topics too, whose names must not leak (POV-219). Mapper logic only — no database.
 */
@QuarkusTest
class ContentItemTopicNameTest {

    private static final User ALICE = UserBuilder.aUser().withId(1L).build();
    private static final User BOB = UserBuilder.aUser().withId(2L).build();

    @Inject
    ContentItemMapper contentItemMapper;

    @Inject
    BriefingMapper briefingMapper;

    private ContentItem item;

    @BeforeEach
    void setUp() {
        ContentSource source = ContentSourceBuilder.aContentSource().withId(3L).build();
        source.topics.add(topic(1L, "Bob's Secret Topic", BOB));
        source.topics.add(topic(5L, "Alice Later Topic", ALICE));
        source.topics.add(topic(4L, "Alice Topic", ALICE));
        item = ContentItemBuilder.aContentItem().withContentSource(source).build();
        item.id = 10L;
    }

    @Test
    void theViewerSeesOnlyTheirOwnTopicName() {
        assertThat(contentItemMapper.toDTO(item, ContentScope.of(ALICE)).topicName()).isEqualTo("Alice Topic");
        assertThat(contentItemMapper.toDTO(item, ContentScope.of(BOB)).topicName()).isEqualTo("Bob's Secret Topic");
    }

    @Test
    void anItemOutsideTheViewersTopicsHasNoTopicName() {
        User carol = UserBuilder.aUser().withId(9L).build();

        assertThat(contentItemMapper.toDTO(item, ContentScope.of(carol)).topicName()).isEqualTo("N/A");
    }

    @Test
    void theUnrestrictedScopeUsesTheFirstTopic() {
        assertThat(contentItemMapper.toDTO(item, ContentScope.unrestricted()).topicName()).isEqualTo("Bob's Secret Topic");
    }

    @Test
    void briefingItemsUseTheBriefingOwnersTopics() {
        Topic aliceTopic = topic(4L, "Alice Topic", ALICE);
        Briefing briefing = BriefingBuilder.aBriefing().withId(20L).withTopic(aliceTopic)
                .withSummaries(new HashSet<>(Set.of(SummaryBuilder.aSummary().withId(30L).withContentItem(item).build())))
                .build();

        assertThat(briefingMapper.toDetailDTO(briefing).summaries())
                .singleElement()
                .satisfies(entry -> assertThat(entry.contentItem().topicName()).isEqualTo("Alice Topic"));
    }

    private static Topic topic(Long id, String name, User owner) {
        return TopicBuilder.aTopic().withId(id).withName(name).withUser(owner).build();
    }
}
