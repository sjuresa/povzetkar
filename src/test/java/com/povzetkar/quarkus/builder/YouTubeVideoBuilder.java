package com.povzetkar.quarkus.builder;

import com.povzetkar.quarkus.model.ContentSource;
import com.povzetkar.quarkus.model.ProcessingStatus;
import com.povzetkar.quarkus.model.YouTubeVideo;

import java.time.Duration;
import java.time.Instant;

public class YouTubeVideoBuilder {
    private Long id;
    private ContentSource contentSource;
    private String videoId = "defaultVideoId";
    private String title = "Default Video Title";
    private String url = "https://youtube.com/watch?v=defaultVideoId";
    private String authorName = "Default Author";
    private String channelId = "defaultChannelId";
    private Instant publishedAt = Instant.now();
    private Duration duration = Duration.ofMinutes(10);
    private ProcessingStatus processingStatus = ProcessingStatus.COMPLETED;

    private YouTubeVideoBuilder() {
    }

    public static YouTubeVideoBuilder aYouTubeVideo() {
        return new YouTubeVideoBuilder();
    }

    public YouTubeVideoBuilder withId(Long id) {
        this.id = id;
        return this;
    }

    public YouTubeVideoBuilder withContentSource(ContentSource contentSource) {
        this.contentSource = contentSource;
        return this;
    }

    public YouTubeVideoBuilder withVideoId(String videoId) {
        this.videoId = videoId;
        this.url = "https://youtube.com/watch?v=" + videoId;
        return this;
    }

    public YouTubeVideoBuilder withTitle(String title) {
        this.title = title;
        return this;
    }

    public YouTubeVideoBuilder withPublishedAt(Instant publishedAt) {
        this.publishedAt = publishedAt;
        return this;
    }

    public YouTubeVideoBuilder withDuration(Duration duration) {
        this.duration = duration;
        return this;
    }

    public YouTubeVideoBuilder withProcessingStatus(ProcessingStatus processingStatus) {
        this.processingStatus = processingStatus;
        return this;
    }

    public YouTubeVideo build() {
        YouTubeVideo video = new YouTubeVideo(videoId, title, url, authorName, channelId, publishedAt, duration);
        video.id = id;
        video.contentSource = contentSource;
        video.processingStatus = processingStatus;
        video.rawContent = "Default raw content";
        return video;
    }
}
