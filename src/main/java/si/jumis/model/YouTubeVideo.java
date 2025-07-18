package si.jumis.model;

import java.time.Duration;
import java.time.Instant;

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
) {}