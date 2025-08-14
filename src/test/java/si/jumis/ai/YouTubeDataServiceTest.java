package si.jumis.ai;

import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.Author;

import java.io.IOException;
import java.math.BigInteger;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class YouTubeDataServiceTest {

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String VALID_CHANNEL_ID = "UC-test-channel-id";
    private static final String INVALID_CHANNEL_ID = "invalid-id";
    private static final String TEST_AUTHOR_TITLE = "Test Author";
    private static final String TEST_AUTHOR_CUSTOM_URL = "@test";
    private static final long TEST_AUTHOR_SUBSCRIBERS = 12345L;
    private static final String UPLOADS_PLAYLIST_ID = "UU-test-channel-id";

    @Mock
    private YouTube mockYouTube;
    @Mock
    private YouTube.Channels mockChannels;
    @Mock
    private YouTube.Channels.List mockChannelsList;

    private YouTubeDataService youtubeDataService;
    private Author testAuthor;

    @BeforeEach
    void setUp() throws IOException {
        youtubeDataService = new YouTubeDataService(FAKE_API_KEY, mockYouTube);
        testAuthor = new Author(TEST_AUTHOR_CUSTOM_URL, TEST_AUTHOR_TITLE, VALID_CHANNEL_ID, TEST_AUTHOR_SUBSCRIBERS);

        // Common mock setup
        when(mockYouTube.channels()).thenReturn(mockChannels);
        when(mockChannels.list(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.setKey(FAKE_API_KEY)).thenReturn(mockChannelsList);
    }

    @Test
    @DisplayName("getAuthorDetails should return author for valid channel ID")
    void getAuthorDetails_shouldReturnAuthor() throws IOException {
        // Arrange
        List<String> expectedParts = List.of("snippet", "statistics");
        Channel mockChannel = createMockAuthorChannel(testAuthor);
        setupMockChannelsList(VALID_CHANNEL_ID, List.of(mockChannel));

        // Act
        Author result = youtubeDataService.getAuthorDetails(VALID_CHANNEL_ID);

        // Assert
        assertEquals(testAuthor, result);
        verify(mockChannels).list(expectedParts);
        verify(mockChannelsList).setId(List.of(VALID_CHANNEL_ID));
    }

    @Test
    @DisplayName("getAuthorDetails should throw IOException for invalid channel ID")
    void getAuthorDetails_withInvalidId_shouldThrowIOException() throws IOException {
        // Arrange
        setupMockChannelsList(INVALID_CHANNEL_ID, Collections.emptyList()); // No channel found

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> youtubeDataService.getAuthorDetails(INVALID_CHANNEL_ID));
        assertTrue(exception.getMessage().contains("Channel not found for ID: " + INVALID_CHANNEL_ID));
        verify(mockChannelsList).setId(List.of(INVALID_CHANNEL_ID));
    }

    @Test
    @DisplayName("getUploadsPlaylistId should return the correct playlist ID for a valid channel")
    void getUploadsPlaylistId_shouldReturnCorrectPlaylistId() throws IOException {
        // Arrange
        List<String> expectedParts = List.of("contentDetails");
        Channel mockChannel = createMockUploadsChannel(VALID_CHANNEL_ID, UPLOADS_PLAYLIST_ID);
        setupMockChannelsList(VALID_CHANNEL_ID, List.of(mockChannel));

        // Act
        String actualUploadsId = youtubeDataService.getUploadsPlaylistId(VALID_CHANNEL_ID);

        // Assert
        assertEquals(UPLOADS_PLAYLIST_ID, actualUploadsId);
        verify(mockChannels).list(expectedParts);
        verify(mockChannelsList).setId(List.of(VALID_CHANNEL_ID));
    }

    // --- Helper Methods ---

    private void setupMockChannelsList(String channelId, List<Channel> channels) throws IOException {
        when(mockChannelsList.setId(List.of(channelId))).thenReturn(mockChannelsList);

        ChannelListResponse response = new ChannelListResponse().setItems(channels);
        when(mockChannelsList.execute()).thenReturn(response);
    }

    private Channel createMockAuthorChannel(Author author) {
        return new Channel()
                .setId(author.channelId())
                .setSnippet(new ChannelSnippet().setTitle(author.name()).setCustomUrl(author.handle()))
                .setStatistics(new ChannelStatistics().setSubscriberCount(BigInteger.valueOf(author.subscriberCount())));
    }

    private Channel createMockUploadsChannel(String channelId, String uploadsPlaylistId) {
        return new Channel()
                .setId(channelId)
                .setContentDetails(new ChannelContentDetails()
                        .setRelatedPlaylists(new ChannelContentDetails.RelatedPlaylists()
                                .setUploads(uploadsPlaylistId)));
    }
}
