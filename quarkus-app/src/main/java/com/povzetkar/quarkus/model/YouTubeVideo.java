package com.povzetkar.quarkus.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.DiscriminatorValue;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

@Entity
@DiscriminatorValue("YouTubeVideo")
public class YouTubeVideo extends ContentItem {
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("d.M.yyyy HH:mm:ss");

    @Column(name = "video_id", unique = true)
    public String videoId;

    @Column
    public String title;

    @Column(name = "author_name")
    public String authorName;

    @Column(name = "channel_id")
    public String channelId;

    @Column(columnDefinition = "TEXT")
    public String transcript;

    @Column(name = "duration_seconds")
    @JdbcTypeCode(SqlTypes.INTEGER)
    public Duration duration;

    public YouTubeVideo() {
    }

    /**
     * @param videoId          The unique ID of the video (e.g., "dSC1mmt-yEE").
     * @param title       The title of the video.
     * @param url         The full watch URL for the video.
     * @param authorName  The display name of the channel that uploaded the video.
     * @param channelId   The unique ID of the author's channel.
     * @param publishedAt The exact date and time the video was published.
     * @param duration    The length of the video.
     */
    public YouTubeVideo(
            String videoId,
            String title,
            String url,
            String authorName,
            String channelId,
            Instant publishedAt,
            Duration duration
    ) {
        this.videoId = videoId;
        this.title = title;
        this.sourceUrl = url;
        this.authorName = authorName;
        this.channelId = channelId;
        this.publishedAt = publishedAt;
        this.duration = duration;
    }

    /**
     * Formats the video's duration into a human-readable HH:MM:SS or MM:SS format.
     *
     * @return A formatted string representing the duration.
     */
    public String formattedDuration() {
        if (duration == null) {
            return "N/A";
        }
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();
        if (hours > 0) {
            return String.format("%d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%d:%02d", minutes, seconds);
        }
    }

    public String formattedPublishedAt() {
        if (publishedAt == null) {
            return "N/A";
        }
        return publishedAt.atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER);
    }


    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        YouTubeVideo that = (YouTubeVideo) obj;
        return Objects.equals(id, that.id) &&
                Objects.equals(videoId, that.videoId) &&
                Objects.equals(title, that.title) &&
                Objects.equals(sourceUrl, that.sourceUrl) &&
                Objects.equals(authorName, that.authorName) &&
                Objects.equals(channelId, that.channelId) &&
                Objects.equals(publishedAt, that.publishedAt) &&
                Objects.equals(this.duration, that.duration);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, videoId, title, sourceUrl, authorName, channelId, publishedAt, duration);
    }

    @Override
    public String toString() {
        return "YouTubeVideo[" +
                "id=" + id + ", " +
                "videoId=" + videoId + ", " +
                "title=" + title + ", " +
                "url=" + sourceUrl + ", " +
                "authorName=" + authorName + ", " +
                "channelId=" + channelId + ", " +
                "publishedAt=" + publishedAt + ", " +
                "duration=" + duration + ']';
    }
}
