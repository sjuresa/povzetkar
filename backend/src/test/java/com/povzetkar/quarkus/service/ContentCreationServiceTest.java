package com.povzetkar.quarkus.service;

import com.google.genai.types.Content;
import com.google.genai.types.Part;
import com.povzetkar.quarkus.model.YouTubeVideo;
import com.povzetkar.quarkus.service.transcript.TranscriptOrchestrator;
import com.povzetkar.quarkus.service.transcript.TranscriptSkippedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContentCreationServiceTest {

    @Mock
    private TranscriptOrchestrator transcriptOrchestrator;

    private ContentCreationService contentCreationService;
    private final ProcessingContext context = ProcessingContext.automatic(Instant.now(), Duration.ofMinutes(15));

    @BeforeEach
    void setUp() {
        contentCreationService = new ContentCreationService(transcriptOrchestrator, 60);
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
            Content content = contentCreationService.createContentForVideo(shortVideo, context);

            // Assert
            assertThat(content.parts()).isPresent();
            List<Part> parts = content.parts().get();
            assertThat(parts).hasSize(2);
            Part videoPart = parts.get(1);
            assertThat(videoPart.fileData()).isPresent();
            assertThat(videoPart.fileData().get().fileUri()).hasValue("url");
        }

        @Test
        @DisplayName("should use existing transcript without fetching if available")
        void shouldUseExistingTranscriptWithoutFetching() {
            // Arrange
            YouTubeVideo videoWithTranscript = new YouTubeVideo("videoWithTranscript", "Video With Transcript", "url", "author", "channel", Instant.now(), Duration.ofMinutes(60));
            videoWithTranscript.transcript = "Existing transcript";

            // Act
            Content content = contentCreationService.createContentForVideo(videoWithTranscript, context);

            // Assert
            assertThat(content.parts()).isPresent();
            List<Part> parts = content.parts().get();
            assertThat(parts).hasSize(1);
            Part textPart = parts.getFirst();
            assertThat(textPart.text()).isPresent();
            assertThat(textPart.text().get()).contains("Existing transcript");
            
            verify(transcriptOrchestrator, never()).fetch(any(), any());
        }

        @Test
        @DisplayName("should use transcript for long videos")
        void shouldUseTranscriptForLongVideos() {
            // Arrange
            YouTubeVideo longVideo = new YouTubeVideo("longVideoId", "Long Video", "url", "author", "channel", Instant.now(), Duration.ofMinutes(60));
            when(transcriptOrchestrator.fetch("longVideoId", context)).thenReturn("This is a transcript.");

            // Act
            Content content = contentCreationService.createContentForVideo(longVideo, context);

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
            when(transcriptOrchestrator.fetch("longVideoId", context)).thenReturn("");

            // Act
            Content content = contentCreationService.createContentForVideo(longVideo, context);

            // Assert
            assertThat(content).isNull();
        }

        @Test
        @DisplayName("should let a skipped transcript fetch (time budget used up) propagate")
        void shouldPropagateSkippedFetch() {
            YouTubeVideo longVideo = new YouTubeVideo("longVideoId", "Long Video", "url", "author", "channel", Instant.now(), Duration.ofMinutes(60));
            when(transcriptOrchestrator.fetch("longVideoId", context)).thenThrow(new TranscriptSkippedException("budget used up"));

            assertThatThrownBy(() -> contentCreationService.createContentForVideo(longVideo, context))
                    .isInstanceOf(TranscriptSkippedException.class);
        }
    }

    @Test
    @DisplayName("should replace all known variables in the prompt")
    void shouldReplaceAllKnownVariables() {
        // Arrange
        YouTubeVideo video = new YouTubeVideo("vid", "Test Title", "https://yt.com/vid", "Author X", "Channel", Instant.now(), Duration.ofMinutes(10));
        video.transcript = "Test Transcript Content";

        // Act
        Content content = contentCreationService.createContentForVideo(video, context);

        // Assert
        assertThat(content.parts()).isPresent();
        String text = content.parts().get().getFirst().text().get();

        assertThat(text).contains("summarizing the video transcript");
        assertThat(text).contains("Test Title");
        assertThat(text).contains("(https://yt.com/vid)");
        assertThat(text).contains("Test Transcript Content");
    }

    @Test
    @DisplayName("should handle null video fields gracefully")
    void shouldHandleNullVideoFields() {
        // Arrange
        YouTubeVideo video = new YouTubeVideo("vid", null, "https://yt.com/dummy", null, "Channel", Instant.now(), Duration.ofMinutes(5));

        // Act
        Content content = contentCreationService.createContentForVideo(video, context);

        // Assert
        assertThat(content.parts()).isPresent();
        String text = content.parts().get().getFirst().text().get();

        assertThat(text).doesNotContain(": null");
        assertThat(text).contains("summarizing the video url:  (https://yt.com/dummy)");
    }
}
