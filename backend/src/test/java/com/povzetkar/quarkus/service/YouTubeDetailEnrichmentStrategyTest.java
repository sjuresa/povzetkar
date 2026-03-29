package com.povzetkar.quarkus.service;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.ChannelStatistics;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@QuarkusTest
class YouTubeDetailEnrichmentStrategyTest {

    @Inject
    @EnrichmentStrategy(SourceTypeEnum.YOUTUBE)
    YouTubeDetailEnrichmentStrategy strategy;

    @InjectMock
    YouTubeDataApiClient mockYouTubeDataApiClient;

    @BeforeEach
    void setup() {
        strategy.clearCache();
        Mockito.reset(mockYouTubeDataApiClient);
        PanacheMock.mock(ContentSource.class);
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
    void testEnrichContentSources_success() throws IOException {
        // Given
        ContentSource source1 = ContentSourceBuilder.aContentSource()
            .withSourceIdentifier("channel1")
            .withName("") // Missing detail
            .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
            .build();
        ContentSource source2 = ContentSourceBuilder.aContentSource()
            .withSourceIdentifier("channel2")
            .withName("") // Missing detail
            .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
            .build();

        when(ContentSource.list(anyString(), any(Object[].class))).thenReturn(List.of(source1, source2));

        Channel channel1 = createTestChannel("channel1", "Channel One", "Desc One", 100L);
        Channel channel2 = createTestChannel("channel2", "Channel Two", "Desc Two", 200L);

        when(mockYouTubeDataApiClient.fetchChannels(List.of("channel1", "channel2")))
                .thenReturn(List.of(channel1, channel2));

        // When
        strategy.enrich();

        // Then
        PanacheMock.verify(ContentSource.class).list(
                "sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')",
                SourceTypeEnum.YOUTUBE.getId());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of("channel1", "channel2"));
        assertEquals("Channel One", source1.name);
        assertEquals("Desc One", source1.description);
        assertEquals(100L, source1.subscriberCount);
        assertEquals("Channel Two", source2.name);
        assertEquals("Desc Two", source2.description);
        assertEquals(200L, source2.subscriberCount);
    }

    @Test
    void testRefreshContentSources_success() throws IOException {
        // Given
        ContentSource source1 = ContentSourceBuilder.aContentSource()
            .withSourceIdentifier("channel1")
            .withName("Old Name One")
            .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
            .build();
        ContentSource source2 = ContentSourceBuilder.aContentSource()
            .withSourceIdentifier("channel2")
            .withName("Old Name Two")
            .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
            .build();
        List sourcesToRefresh = List.of(source1, source2);

        when(ContentSource.list(anyString(), any(Object[].class))).thenReturn(sourcesToRefresh);
        Channel channel1 = createTestChannel("channel1", "New Name One", "New Desc One", 150L);
        Channel channel2 = createTestChannel("channel2", "New Name Two", "New Desc Two", 250L);

        when(mockYouTubeDataApiClient.fetchChannels(List.of("channel1", "channel2")))
                .thenReturn(List.of(channel1, channel2));

        // When
        strategy.refresh();

        // Then
        PanacheMock.verify(ContentSource.class).list("sourceTypeId = ?1", SourceTypeEnum.YOUTUBE.getId());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of("channel1", "channel2"));
        assertEquals("New Name One", source1.name);
        assertEquals("New Desc One", source1.description);
        assertEquals(150L, source1.subscriberCount);
        assertEquals("New Name Two", source2.name);
        assertEquals("New Desc Two", source2.description);
        assertEquals(250L, source2.subscriberCount);
    }

    @Test
    void testEnrichContentSources_apiError() throws IOException {
        // Given
        ContentSource source1 = ContentSourceBuilder.aContentSource()
            .withSourceIdentifier("channel1")
            .withName("")
            .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
            .build();

        when(ContentSource.list(anyString(), any(Object[].class))).thenReturn(List.of(source1));

        when(mockYouTubeDataApiClient.fetchChannels(anyList()))
                .thenThrow(new IOException("API Error"));

        // When
        strategy.enrich();

        // Then
        PanacheMock.verify(ContentSource.class).list(
                "sourceTypeId = ?1 and (name is null or name = '' or description is null or description = '')",
                SourceTypeEnum.YOUTUBE.getId());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of("channel1"));
        // Assert that no changes were made to the source due to error
        assertEquals("", source1.name);
    }

    @Test
    void testFetchDetails_success() throws IOException {
        // Given
        String channelId = "UCtestChannelId123456789"; // 24 chars
        String channelName = "Test Channel";
        String channelDescription = "A test channel description.";
        long subscriberCount = 12345L;

        Channel testChannel = createTestChannel(channelId, channelName, channelDescription, subscriberCount);
        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenReturn(List.of(testChannel));

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(channelId);

        // Then
        assertTrue(details.isPresent());
        assertEquals(channelName, details.get().name());
        assertEquals(channelDescription, details.get().description());
        assertEquals(subscriberCount, details.get().subscriberCount());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of(channelId));
    }

    @Test
    void testFetchDetails_notFound() throws IOException {
        // Given
        String channelId = "UCnonExistentChannel1234"; // 24 chars
        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenReturn(Collections.emptyList());

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(channelId);

        // Then
        assertTrue(details.isEmpty());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of(channelId));
    }

    @Test
    void testFetchDetails_apiError() throws IOException {
        // Given
        String channelId = "UCerrorChannel1234567890"; // 24 chars
        when(mockYouTubeDataApiClient.fetchChannels(List.of(channelId))).thenThrow(new IOException("API Error"));

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(channelId);

        // Then
        assertTrue(details.isEmpty());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannels(List.of(channelId));
    }

    @Test
    void testFetchDetailsByHandle_success() throws IOException {
        // Given
        String handle = "@veritasium";
        String channelName = "Veritasium";
        Channel testChannel = createTestChannel("UCvPyVWLnAgmz841qhX67yag", channelName, "Desc", 1000000L);
        
        // We'll need to mock the new method in YouTubeDataApiClient
        when(mockYouTubeDataApiClient.fetchChannelByHandle(handle)).thenReturn(Optional.of(testChannel));

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(handle);

        // Then
        assertTrue(details.isPresent());
        assertEquals(channelName, details.get().name());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannelByHandle(handle);
    }

    @Test
    void testFetchDetailsByFullUrl_success() throws IOException {
        // Given
        String url = "https://www.youtube.com/@veritasium";
        String channelName = "Veritasium";
        Channel testChannel = createTestChannel("UCvPyVWLnAgmz841qhX67yag", channelName, "Desc", 1000000L);
        
        when(mockYouTubeDataApiClient.fetchChannelByHandle("@veritasium")).thenReturn(Optional.of(testChannel));

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(url);

        // Then
        assertTrue(details.isPresent());
        assertEquals(channelName, details.get().name());
        verify(mockYouTubeDataApiClient, times(1)).fetchChannelByHandle("@veritasium");
    }

    @Test
    void testFetchDetails_invalidUrl() {
        // Given
        String url = "https://www.google.com";

        // When
        Optional<ContentSourceDetailService.ContentSourceDetails> details = strategy.fetchDetails(url);

        // Then
        assertTrue(details.isEmpty());
        verifyNoInteractions(mockYouTubeDataApiClient);
    }
}
