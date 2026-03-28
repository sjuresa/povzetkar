package com.povzetkar.quarkus.service;

import com.povzetkar.quarkus.api.OrchestrationRequest;
import com.povzetkar.quarkus.builder.ContentSourceBuilder;
import com.povzetkar.quarkus.builder.TopicBuilder;
import com.povzetkar.quarkus.builder.UserBuilder;
import com.povzetkar.quarkus.model.ContentItem;
import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.SourceTypeEnum;
import com.povzetkar.quarkus.model.Topic;
import com.povzetkar.quarkus.model.User;
import com.povzetkar.quarkus.model.YouTubeVideo;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.context.ManagedExecutor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@QuarkusTest
class OrchestrationPreviewIntegrationTest {

    @Inject
    OrchestrationManager orchestrationManager;

    @InjectMock
    YouTubeContentFetcherService mockFetcherService;

    @Inject
    ManagedExecutor managedExecutor;
    
    @InjectMock
    UserContextService userContextService;
    
    @InjectMock
    VideoSummaryService videoSummaryService;
    
    @InjectMock
    OrchestrationLockService orchestrationLockService;
    
    @InjectMock
    JustInTimeSummarizationScheduler justInTimeSummarizationScheduler;

    @InjectMock
    EventBroadcaster eventBroadcaster;

    @BeforeEach
    void setUp() {
        Mockito.reset(mockFetcherService, eventBroadcaster, 
            userContextService, videoSummaryService, orchestrationLockService, justInTimeSummarizationScheduler);
        
        // Clean up database
        tearDown();
    }

    @AfterEach
    void tearDown() {
        QuarkusTransaction.requiringNew().run(() -> {
            ContentItem.deleteAll();
            Topic.deleteAll();
            ContentSource.deleteAll();
            User.deleteAll();
        });
    }

    @Test
    void testPreviewMode_FullStatusMarkingIntegration() throws IOException {
        // Given
        LocalDate date = LocalDate.now();
        
        // 1. Setup all DB entities in a single consistent block
        final Long topicId = QuarkusTransaction.requiringNew().call(() -> {
            User user = UserBuilder.aUser().withUsername("int_user_" + java.util.UUID.randomUUID()).withEmail("int@ex.com").build();
            user.persist();
            
            Topic t = TopicBuilder.aTopic().withName("Int Topic").withUser(user).build();
            t.persist();
            
            ContentSource s = ContentSourceBuilder.aContentSource()
                    .withSourceIdentifier("int_source_" + System.currentTimeMillis())
                    .withSourceTypeId(SourceTypeEnum.YOUTUBE.getId())
                    .build();
            s.persist();
            t.contentSources.add(s);
            t.persist();

            // Persist v3 as ALREADY in DB (COMPLETED)
            YouTubeVideo existing = new YouTubeVideo();
            existing.videoId = "existing1";
            existing.title = "Existing Video";
            existing.sourceUrl = "http://yt.com/existing1";
            existing.rawContent = "raw";
            existing.duration = Duration.ofSeconds(400);
            existing.processingStatus = ProcessingStatus.COMPLETED;
            existing.contentSource = s;
            existing.publishedAt = Instant.now();
            existing.persist();

            return t.id;
        });

        // 2. Mock Fetcher Service to return 3 videos:
        // - Video 1: 300s -> Expected PENDING
        // - Video 2: 30s (Too short) -> Expected SKIPPED_TOO_SHORT
        // - Video 3: 400s (Already exists in DB) -> Expected (ignored in counts)
        
        YouTubeVideo v1 = new YouTubeVideo();
        v1.videoId = "long1"; v1.duration = Duration.ofSeconds(300); v1.title = "Long Video"; v1.publishedAt = Instant.now();
        v1.sourceUrl = "http://yt.com/long1"; v1.rawContent = "raw";
        
        YouTubeVideo v2 = new YouTubeVideo();
        v2.videoId = "short1"; v2.duration = Duration.ofSeconds(30); v2.title = "Short Video"; v2.publishedAt = Instant.now();
        v2.sourceUrl = "http://yt.com/short1"; v2.rawContent = "raw";
        
        YouTubeVideo v3 = new YouTubeVideo();
        v3.videoId = "existing1"; v3.duration = Duration.ofSeconds(400); v3.title = "Existing Video"; v3.publishedAt = Instant.now();
        v3.sourceUrl = "http://yt.com/existing1"; v3.rawContent = "raw";

        when(mockFetcherService.fetchYouTubeVideos(any(), any(), any(), any()))
                .thenReturn(List.of(v1, v2, v3));

        // 3. Run Orchestration Preview
        var request = new OrchestrationRequest(date, date, false, false, false, true, topicId);
        orchestrationManager.runOrchestrationManually(request).await().indefinitely();

        // 4. Verify SSE Event message
        // v1 = PENDING (1 new)
        // v2 = SKIPPED_TOO_SHORT (excluded from counts)
        // v3 = Already exists (not new)
        // Expected: "Found 1 items (1 new, 0 existing pending)"
        verify(eventBroadcaster, timeout(10000)).broadcast(argThat(event -> 
            "ORCHESTRATION_PREVIEW_COMPLETED".equals(event.type()) && 
            event.data().toString().contains("Found 1 items (1 new, 0 existing pending)")
        ));

        // 5. Verify Database state
        QuarkusTransaction.requiringNew().run(() -> {
            List<ContentItem> items = ContentItem.listAll();
            // v1 (PENDING), v2 (SHORT), v3 (EXISTING COMPLETED)
            assertEquals(3, items.size());
            
            YouTubeVideo savedV1 = YouTubeVideo.find("videoId", "long1").firstResult();
            assertEquals(ProcessingStatus.PENDING, savedV1.processingStatus);
            
            YouTubeVideo savedV2 = YouTubeVideo.find("videoId", "short1").firstResult();
            assertEquals(ProcessingStatus.SKIPPED_TOO_SHORT, savedV2.processingStatus);
        });
    }
}
