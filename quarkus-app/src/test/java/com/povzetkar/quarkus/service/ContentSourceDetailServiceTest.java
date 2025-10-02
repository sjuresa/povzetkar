package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.YouTube.Channels;
import com.google.api.services.youtube.YouTube.Channels.List;
import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.ChannelStatistics;
import com.povzetkar.quarkus.model.ContentSource;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

@QuarkusTest
class ContentSourceDetailServiceTest {

    ContentSourceDetailService service;

    private List mockList;

    @BeforeEach
    void setUp() throws IOException {
        YouTube mockYouTube = Mockito.mock(YouTube.class);
        Channels mockChannels = Mockito.mock(Channels.class);
        mockList = Mockito.mock(List.class);

        when(mockYouTube.channels()).thenReturn(mockChannels);
        when(mockChannels.list(anyList())).thenReturn(mockList);
        when(mockList.setKey(anyString())).thenReturn(mockList);
        when(mockList.setId(anyList())).thenReturn(mockList);

        service = new ContentSourceDetailService("testApiKey", mockYouTube);
    }

    @Test
    void testFetchContentSourceDetails_success() throws IOException {
        String channelId = "testChannelId";
        String channelName = "Test Channel";
        String channelDescription = "A test channel description.";
        long subscriberCount = 12345L;

        ChannelListResponse response = getChannelListResponse(channelName, channelDescription, subscriberCount);

        when(mockList.execute()).thenReturn(response);

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isPresent());
        assertEquals(channelName, details.get().name());
        assertEquals(channelDescription, details.get().description());
        assertEquals(subscriberCount, details.get().subscriberCount());
    }

    private static ChannelListResponse getChannelListResponse(String channelName, String channelDescription, long subscriberCount) {
        Channel channel = new Channel();
        ChannelSnippet snippet = new ChannelSnippet();
        snippet.setTitle(channelName);
        snippet.setDescription(channelDescription);
        channel.setSnippet(snippet);

        ChannelStatistics statistics = new ChannelStatistics();
        statistics.setSubscriberCount(BigInteger.valueOf(subscriberCount));
        channel.setStatistics(statistics);

        ChannelListResponse response = new ChannelListResponse();
        response.setItems(Collections.singletonList(channel));
        return response;
    }

    @Test
    void testFetchContentSourceDetails_channelNotFound() throws IOException {
        String channelId = "nonExistentChannel";

        ChannelListResponse response = new ChannelListResponse();
        response.setItems(Collections.emptyList());

        when(mockList.execute()).thenReturn(response);

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isEmpty());
    }

    @Test
    void testFetchContentSourceDetails_apiError() throws IOException {
        String channelId = "errorChannel";

        when(mockList.execute()).thenThrow(new IOException("API Error"));

        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails(channelId);

        assertTrue(details.isEmpty());
    }

    @Test
    @Transactional
    void testEnrichContentSources_updatesAndPersistsData() throws IOException {
        // Arrange
        ContentSource realSource = new ContentSource();
        realSource.sourceIdentifier = "testChannelId";
        realSource.name = ""; // Mark for enrichment
        ContentSource sourceSpy = spy(realSource);
        // Stub the persist() method to do nothing
        doNothing().when(sourceSpy).persist();

        PanacheMock.mock(ContentSource.class);
        when(ContentSource.list(anyString(), any(Object[].class))).thenReturn(java.util.List.of(sourceSpy));

        String channelName = "Enriched Channel";
        String channelDesc = "Enriched Description";
        long subCount = 5000L;
        ChannelListResponse response = getChannelListResponse(channelName, channelDesc, subCount);
        when(mockList.execute()).thenReturn(response);

        // Act
        service.enrichContentSources();

        // Assert
        Mockito.verify(sourceSpy).persist();
        assertEquals(channelName, sourceSpy.name);
        assertEquals(channelDesc, sourceSpy.description);
        assertEquals(subCount, sourceSpy.subscriberCount);
    }

    @Test
    void testFetchContentSourceDetails_nullStatistics() throws IOException {
        // Arrange
        Channel channel = new Channel();
        ChannelSnippet snippet = new ChannelSnippet();
        snippet.setTitle("Test Channel");
        channel.setSnippet(snippet);
        channel.setStatistics(null); // Explicitly set statistics to null

        ChannelListResponse response = new ChannelListResponse();
        response.setItems(Collections.singletonList(channel));
        when(mockList.execute()).thenReturn(response);

        // Act
        Optional<ContentSourceDetailService.ContentSourceDetails> details = service.fetchContentSourceDetails("anyId");

        // Assert
        assertTrue(details.isPresent());
        assertEquals(0L, details.get().subscriberCount());
    }
}
