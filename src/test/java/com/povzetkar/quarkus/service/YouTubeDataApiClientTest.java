package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.VideoListResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@QuarkusTest
class YouTubeDataApiClientTest {

    @Inject
    YouTubeDataApiClient client;

    @Inject
    @Alternative
    YouTube mockYouTubeService;

    @BeforeEach
    void setUp() {
        Mockito.reset(YouTubeMockProducer.STATIC_MOCK_YOUTUBE);
    }

    @Test
    void testFetchChannels_returnsEmptyListWhenApiReturnsNull() throws IOException {
        // Arrange
        ChannelListResponse mockResponse = new ChannelListResponse();
        mockResponse.setItems(null); // Simulate API returning null

        when(mockYouTubeService.channels().list(Mockito.anyList()).setId(Mockito.anyList()).setKey(Mockito.anyString()).execute())
                .thenReturn(mockResponse);

        // Act
        List<com.google.api.services.youtube.model.Channel> channels = client.fetchChannels(Collections.singletonList("id"));

        // Assert
        assertNotNull(channels);
        assertEquals(0, channels.size());
    }

    @Test
    void testFetchVideoDurations_returnsEmptyMapWhenApiReturnsNull() throws IOException {
        // Arrange
        VideoListResponse mockResponse = new VideoListResponse();
        mockResponse.setItems(null); // Simulate API returning null

        when(mockYouTubeService.videos().list(Mockito.anyList()).setId(Mockito.anyList()).setKey(Mockito.anyString()).execute())
                .thenReturn(mockResponse);

        // Act
        Map<String, java.time.Duration> durations = client.fetchVideoDurations(Collections.singletonList("id"));

        // Assert
        assertNotNull(durations);
        assertEquals(0, durations.size());
    }
}
