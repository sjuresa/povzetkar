package si.jumis.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import si.jumis.model.YouTubeVideo;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentCreationServiceTest {

    @Mock
    private YouTubeTranscriptService transcriptService;

    private ContentCreationService contentCreationService;

    @BeforeEach
    void setUp() {
        contentCreationService = new ContentCreationService(transcriptService);
    }

    @Nested
    @DisplayName("createContentForVideo")
    class CreateContentForVideo {

        @Test
        @DisplayName("should use video URL for short videos")
        void shouldUseVideoUrlForShortVideos() {
            // Arrange
            YouTubeVideo shortVideo = new YouTubeVideo("shortVideoId", "Short Video", "url", "author", "channel", Instant.now(), Duration.ofMinutes(5));

            // Act
            Content content = contentCreationService.createContentForVideo(shortVideo);

            // Assert
            assertThat(content.parts()).isPresent();
            List<Part> parts = content.parts().get();
            assertThat(parts).hasSize(2);
            Part videoPart = parts.get(1);
            assertThat(videoPart.fileData()).isPresent();
            assertThat(videoPart.fileData().get().fileUri()).hasValue("url");
        }

        @Test
        @DisplayName("should use transcript for long videos")
        void shouldUseTranscriptForLongVideos() {
            // Arrange
            YouTubeVideo longVideo = new YouTubeVideo("longVideoId", "Long Video", "url", "author", "channel", Instant.now(), Duration.ofMinutes(60));
            when(transcriptService.getTranscript("longVideoId")).thenReturn("This is a transcript.");

            // Act
            Content content = contentCreationService.createContentForVideo(longVideo);

            // Assert
            assertThat(content.parts()).isPresent();
            List<Part> parts = content.parts().get();
            assertThat(parts).hasSize(1);
            Part textPart = parts.getFirst();
            assertThat(textPart.text()).isPresent();
            assertThat(textPart.text().get()).contains("This is a transcript.");
        }

        @Test
        @DisplayName("should return null if transcript is not available for long video")
        void shouldReturnNullIfTranscriptNotAvailable() {
            // Arrange
            YouTubeVideo longVideo = new YouTubeVideo("longVideoId", "Long Video", "url", "author", "channel", Instant.now(), Duration.ofMinutes(60));
            when(transcriptService.getTranscript("longVideoId")).thenReturn("");

            // Act
            Content content = contentCreationService.createContentForVideo(longVideo);

            // Assert
            assertThat(content).isNull();
        }
    }
}
