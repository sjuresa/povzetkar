package com.povzetkar.quarkus.service;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.ChannelListResponse;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoContentDetails;
import com.google.api.services.youtube.model.VideoListResponse;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
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

    /**
     * YouTube reports no duration ({@code null}) or {@code P0D} for a live or upcoming video; such a video has no
     * known duration yet and is left out instead of failing the whole fetch (POV-235).
     */
    @Test
    void fetchVideoDurations_keepsOnlyKnownPositiveDurations() throws IOException {
        VideoListResponse mockResponse = new VideoListResponse();
        mockResponse.setItems(List.of(video("finished", "PT5M"), video("premiere", null), video("live", "P0D"),
                video("weeks", "P1W")));

        when(mockYouTubeService.videos().list(Mockito.anyList()).setId(Mockito.anyList()).setKey(Mockito.anyString()).execute())
                .thenReturn(mockResponse);

        Map<String, Duration> durations = client.fetchVideoDurations(List.of("finished", "premiere", "live", "weeks"));

        assertEquals(Map.of("finished", Duration.ofMinutes(5)), durations);
    }

    /** {@code videos.list} accepts at most 50 IDs per call; more are requested in batches and merged (POV-235). */
    @Test
    void fetchVideoDurations_requestsAtMostFiftyIdsPerCall() throws IOException {
        List<String> ids = java.util.stream.IntStream.range(0, 120).mapToObj(i -> "v" + i).toList();
        VideoListResponse first = new VideoListResponse().setItems(List.of(video("v0", "PT1M")));
        VideoListResponse second = new VideoListResponse().setItems(List.of(video("v50", "PT2M")));
        VideoListResponse third = new VideoListResponse().setItems(List.of(video("v100", "PT3M")));
        YouTube.Videos.List request = mockYouTubeService.videos().list(Mockito.anyList());
        when(request.setId(Mockito.anyList()).setKey(Mockito.anyString()).execute()).thenReturn(first, second, third);
        Mockito.clearInvocations(request); // the stubbing above called setId once

        Map<String, Duration> durations = client.fetchVideoDurations(ids);

        @SuppressWarnings("unchecked")
        org.mockito.ArgumentCaptor<List<String>> batches = org.mockito.ArgumentCaptor.forClass(List.class);
        Mockito.verify(request, Mockito.times(3)).setId(batches.capture());
        assertEquals(List.of(50, 50, 20), batches.getAllValues().stream().map(List::size).toList());
        assertEquals(Map.of("v0", Duration.ofMinutes(1), "v50", Duration.ofMinutes(2), "v100", Duration.ofMinutes(3)),
                durations);
    }

    private static Video video(String id, String isoDuration) {
        return new Video().setId(id).setContentDetails(new VideoContentDetails().setDuration(isoDuration));
    }
}
