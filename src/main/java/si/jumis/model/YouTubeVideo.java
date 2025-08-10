package si.jumis.model;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;

import static si.jumis.Povzetkar.DATE_TIME_FORMATTER;

/**
 * An immutable data class representing a single YouTube video.
 *
 * @param id     The unique ID of the video (e.g., "dSC1mmt-yEE").
 * @param title       The title of the video.
 * @param url         The full watch URL for the video.
 * @param authorName  The display name of the channel that uploaded the video.
 * @param channelId   The unique ID of the author's channel.
 * @param publishedAt The exact date and time the video was published.
 * @param duration    The length of the video.
 */
public record YouTubeVideo(
        String id,
        String title,
        String url,
        String authorName,
        String channelId,
        Instant publishedAt,
        Duration duration
) {
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
}