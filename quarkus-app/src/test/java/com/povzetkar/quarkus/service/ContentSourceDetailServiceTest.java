package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.model.Channel;
import com.google.api.services.youtube.model.ChannelSnippet;
import com.google.api.services.youtube.model.ChannelStatistics;
import com.povzetkar.quarkus.model.ContentSource;
import io.quarkus.panache.mock.PanacheMock;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@QuarkusTest
class ContentSourceDetailServiceTest {

    @InjectMock
    YouTubeDataApiClient mockYouTubeDataApiClient;

    @Inject
    ContentSourceDetailService service;

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
}
