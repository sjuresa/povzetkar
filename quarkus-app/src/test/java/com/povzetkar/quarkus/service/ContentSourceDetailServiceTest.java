package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.ChannelStatistics;
import com.povzetkar.quarkus.model.ContentSource;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

@QuarkusTest
class ContentSourceDetailServiceTest {

    @InjectMock
    YouTubeDataApiClient mockYouTubeDataApiClient;

    @Inject
    ContentSourceDetailService service;

    private static final int YOUTUBE_SOURCE_TYPE_ID = 1;

    @BeforeAll
    static void setupPanache() {
        PanacheMock.mock(ContentSource.class);
    }

    @Test
    void testFetchContentSourceDetails_success() throws IOException {
        String channelId = "testChannelId";
        String channelName = "Test Channel";
        String channelDescription = "A test channel description.";
        long subscriberCount = 12345L;

        List<Channel> channels = List.of(createTestChannel(channelId, channelName, channelDescription, subscriberCount));

        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenReturn(channels);

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isPresent());
        assertEquals(channelName, details.get().name());
        assertEquals(channelDescription, details.get().description());
        assertEquals(subscriberCount, details.get().subscriberCount());
    }

    private static Channel createTestChannel(String channelId, String channelName, String channelDescription, long subscriberCount) {
        Channel channel = new Channel();
        channel.setId(channelId);
        ChannelSnippet snippet = new ChannelSnippet();
        snippet.setTitle(channelName);
        snippet.setDescription(channelDescription);
        channel.setSnippet(snippet);

        ChannelStatistics statistics = new ChannelStatistics();
        statistics.setSubscriberCount(BigInteger.valueOf(subscriberCount));
        channel.setStatistics(statistics);
        return channel;
    }

    @Test
    void testFetchContentSourceDetails_channelNotFound() throws IOException {
        String channelId = "nonExistentChannel";

        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenReturn(Collections.emptyList());

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isEmpty());
    }

    @Test
    void testFetchContentSourceDetails_apiError() throws IOException {
        String channelId = "errorChannel";

        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenThrow(new IOException("API Error"));

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isEmpty());
    }

    @Test
    void testFetchContentSourceDetails_nullStatistics() throws IOException {
        // Arrange
        Channel channel = new Channel();
        channel.setId("anyId");
        ChannelSnippet snippet = new ChannelSnippet();
        snippet.setTitle("Test Channel");
        channel.setSnippet(snippet);
        channel.setStatistics(null); // Explicitly set statistics to null

        when(mockYouTubeDataApiClient.fetchChannels(List.of("anyId"))).thenReturn(List.of(channel));

        // Act
        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails("anyId");

        // Assert
        assertTrue(details.isPresent());
        assertEquals(0L, details.get().subscriberCount());
    }

    @Test
    void testEnrichAndRefreshContentSources() throws IOException {
        // Arrange
        ContentSource source1 = new ContentSource();
        source1.sourceIdentifier = "channel1";
        source1.sourceTypeId = YOUTUBE_SOURCE_TYPE_ID;

        ContentSource source2 = new ContentSource();
        source2.sourceIdentifier = "channel2";
        source2.description = ""; // Needs enrichment
        source2.sourceTypeId = YOUTUBE_SOURCE_TYPE_ID;

        List<PanacheEntityBase> sourcesToEnrich = new ArrayList<>(List.of(source1, source2));
        when(ContentSource.list("sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')",
                YOUTUBE_SOURCE_TYPE_ID)).thenReturn(sourcesToEnrich);
        // can't mock static method
//        when(ContentSource.persist()).doNothing;

        Channel channel1 = createTestChannel("channel1", "Channel One", "Desc One", 100L);
        Channel channel2 = createTestChannel("channel2", "Channel Two", "Desc Two", 200L);
        List<String> channelIds = List.of("channel1", "channel2");
        when(mockYouTubeDataApiClient.fetchChannels(channelIds)).thenReturn(List.of(channel1, channel2));

        // Act
        service.enrichContentSources();

        // Assert
        assertAll(
                () -> assertEquals("Channel One", source1.name),
                () -> assertEquals("Desc One", source1.description),
                () -> assertEquals(100L, source1.subscriberCount)
        );
        assertAll(
                () -> assertEquals("Channel Two", source2.name),
                () -> assertEquals("Desc Two", source2.description),
                () -> assertEquals(200L, source2.subscriberCount)
        );

//        PanacheMock.verify(ContentSource.class).persist(source1);
//        PanacheMock.verify(ContentSource.class).persist(source2);

        // Arrange
        ContentSource source3 = new ContentSource();
        source3.sourceIdentifier = "channel3";
        source3.name = "Old Name 1";
        source3.subscriberCount = 50;
        source3.sourceTypeId = YOUTUBE_SOURCE_TYPE_ID;

        ContentSource source4 = new ContentSource();
        source4.sourceIdentifier = "channel4";
        source4.name = "Old Name 2";
        source4.subscriberCount = 150;
        source4.sourceTypeId = YOUTUBE_SOURCE_TYPE_ID;

        List<PanacheEntityBase> allSources = new ArrayList<>(List.of(source3, source4));
        when(ContentSource.list("sourceTypeId = ?1", YOUTUBE_SOURCE_TYPE_ID)).thenReturn(allSources);


        Channel channel3 = createTestChannel("channel3", "New Name 1", "New Desc 1", 101L);
        Channel channel4 = createTestChannel("channel4", "New Name 2", "New Desc 2", 202L);
        List<String> channelIds1 = List.of("channel3", "channel4");
        when(mockYouTubeDataApiClient.fetchChannels(channelIds1)).thenReturn(List.of(channel3, channel4));

        // Act
        service.refreshAllContentSources();

        // Assert
        assertAll(
                () -> assertEquals("New Name 1", source3.name),
                () -> assertEquals(101L, source3.subscriberCount)
        );
        assertAll(
                () -> assertEquals("New Name 2", source4.name),
                () -> assertEquals(202L, source4.subscriberCount)
        );

//        PanacheMock.verify(ContentSource.class).persist(source1);
//        PanacheMock.verify(ContentSource.class).persist(source2);
    }
}
