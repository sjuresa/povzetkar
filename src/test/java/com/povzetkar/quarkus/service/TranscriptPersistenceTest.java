package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;

import com.google.genai.types.Content;
import com.povzetkar.quarkus.ai.GenAiClient;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Summary;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@QuarkusTest
class TranscriptPersistenceTest {

    @Inject
    YouTubeSummarizationStrategy strategy;

    @InjectMock
    ContentCreationService contentCreationService;

    @InjectMock
    VideoSummaryService videoSummaryService;

    private YouTubeVideo testVideo;
    private GenAiClient mockClient;

    @BeforeEach
    @Transactional
    void setUp() {
        User user = UserBuilder.aUser()
			.withUsername("testuser")
			.withEmail("testuser@example.com")
			.withName("Test User")
			.build();
        user.persist();

        Topic topic = TopicBuilder.aTopic()
			.withName("Test Topic")
			.withUser(user)
			.build();
        topic.persist();

        ContentSource source = ContentSourceBuilder.aContentSource()
			.withSourceIdentifier("test_channel")
			.withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
			.withName("Test Channel")
			.build();
        source.persist();

        testVideo = new YouTubeVideo();
        testVideo.videoId = "test_video_id";
        testVideo.title = "Test Video";
        testVideo.sourceUrl = "http://youtube.com/watch?v=test_video_id";
        testVideo.publishedAt = Instant.now();
        testVideo.duration = Duration.ofMinutes(10);
        testVideo.contentSource = source;
        testVideo.rawContent = "";
        testVideo.persist();

        mockClient = mock(GenAiClient.class);
    }

    @AfterEach
    @Transactional
    void tearDown() {
        Summary.deleteAll();
        ContentItem.deleteAll();
        ContentSource.deleteAll();
        Topic.deleteAll();
        User.deleteAll();
    }

    @Test
    void testTranscriptIsPersisted() {
        // Arrange
        String expectedTranscript = "This is the transcript text.";
        
        // Mock ContentCreationService to simulate fetching transcript and updating the detached object
        when(contentCreationService.createContentForVideo(any(YouTubeVideo.class))).thenAnswer(invocation -> {
            YouTubeVideo video = invocation.getArgument(0);
            video.transcript = expectedTranscript; // Simulate side effect
            return mock(Content.class);
        });

        when(videoSummaryService.generateContent(any(), any(), anyString(), anyString(), anyString())).thenReturn("Summary text");

        // Act
        // We pass the detached entity (fetched in setUp but transaction closed)
        // Actually, since setUp is @Transactional, the entity might be detached or not depending on how QuarkusTest handles it.
        // To be sure, let's fetch it again in a new transaction scope or just rely on the fact that 'testVideo' is an object reference.
        // The strategy uses QuarkusTransaction.requiringNew() internally, so it will fetch its own managed instance.
        
        // Important: The 'testVideo' object passed here will be modified by the mock above.
        strategy.summarize(mockClient, testVideo, LocalDate.now());

        // Assert
        // Verify the database state
        YouTubeVideo persistedVideo = YouTubeVideo.findById(testVideo.id);
        assertNotNull(persistedVideo);
        assertEquals(expectedTranscript, persistedVideo.transcript, "Transcript should be persisted to the database");
        assertEquals(com.povzetkar.quarkus.model.ProcessingStatus.COMPLETED, persistedVideo.processingStatus, "Processing status should be COMPLETED");
    }
}
