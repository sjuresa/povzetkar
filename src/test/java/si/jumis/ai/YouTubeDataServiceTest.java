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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class YouTubeDataServiceTest {

    private static final String FAKE_API_KEY = "test-api-key";
    private static final String VALID_CHANNEL_ID = "UC-test-channel-id";
    private static final String TEST_AUTHOR_TITLE = "Test Author";
    private static final String TEST_AUTHOR_CUSTOM_URL = "@test";
    private static final long TEST_AUTHOR_SUBSCRIBERS = 12345L;

    @Mock
    private YouTube mockYouTube;
    @Mock
    private YouTube.Channels mockChannels;
    @Mock
    private YouTube.Channels.List mockChannelsList;

    private YouTubeDataService youtubeDataService;
    private Author testAuthor;

    @BeforeEach
    void setUp() {
        youtubeDataService = new YouTubeDataService(FAKE_API_KEY, mockYouTube);
        testAuthor = new Author(TEST_AUTHOR_CUSTOM_URL, TEST_AUTHOR_TITLE, VALID_CHANNEL_ID, TEST_AUTHOR_SUBSCRIBERS);
    }

    @Test
    @DisplayName("getAuthorDetails should return author for valid channel ID")
    void getAuthorDetails_shouldReturnAuthor() throws IOException {
        // Arrange
        setupChannelsApiMocks();
        mockChannelsResponse(List.of(createMockChannel(testAuthor)));

        // Act
        Author result = youtubeDataService.getAuthorDetails(VALID_CHANNEL_ID);

        // Assert
        assertEquals(testAuthor, result);
    }

    @Test
    @DisplayName("getAuthorDetails should throw IOException for invalid channel ID")
    void getAuthorDetails_withInvalidId_shouldThrowIOException() throws IOException {
        // Arrange
        setupChannelsApiMocks();
        mockChannelsResponse(Collections.emptyList()); // No channel found

        // Act & Assert
        IOException exception = assertThrows(IOException.class, () -> youtubeDataService.getAuthorDetails("invalid-id"));
        assertTrue(exception.getMessage().contains("Channel not found for ID: invalid-id"));
    }

    @Test
    @DisplayName("getUploadsPlaylistId should return the correct playlist ID for a valid channel")
    void getUploadsPlaylistId_shouldReturnCorrectPlaylistId() throws IOException {
        // Arrange
        String expectedUploadsId = "UU-test-channel-id";
        setupChannelsApiMocks();

        Channel mockChannel = new Channel()
                .setId(VALID_CHANNEL_ID)
                .setContentDetails(new ChannelContentDetails()
                        .setRelatedPlaylists(new ChannelContentDetails.RelatedPlaylists()
                                .setUploads(expectedUploadsId)));

        mockChannelsResponse(List.of(mockChannel));

        // Act
        String actualUploadsId = youtubeDataService.getUploadsPlaylistId(VALID_CHANNEL_ID);

        // Assert
        assertEquals(expectedUploadsId, actualUploadsId);
        verify(mockChannels).list(List.of("contentDetails"));
    }

    // --- Helper Methods ---

    private void setupChannelsApiMocks() throws IOException {
        when(mockYouTube.channels()).thenReturn(mockChannels);
        when(mockChannels.list(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.setKey(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.setId(any())).thenReturn(mockChannelsList);
        when(mockChannelsList.execute()).thenAnswer(invocation -> new ChannelListResponse()); // Default empty response
    }

    private void mockChannelsResponse(List<Channel> channels) throws IOException {
        ChannelListResponse response = new ChannelListResponse().setItems(channels);
        when(mockChannelsList.execute()).thenReturn(response);
    }

    private Channel createMockChannel(Author author) {
        return new Channel()
                .setId(author.channelId())
                .setSnippet(new ChannelSnippet().setTitle(author.name()).setCustomUrl(author.handle()))
                .setStatistics(new ChannelStatistics().setSubscriberCount(BigInteger.valueOf(author.subscriberCount())));
    }
}
