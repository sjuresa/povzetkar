package si.jumis.model;

/**
 * Represents a YouTube channel author.
 *
 * @param handle          The author's handle (e.g., "@AuthorHandle").
 * @param name            The display name of the channel.
 * @param channelId       The unique ID of the channel.
 * @param subscriberCount The number of subscribers.
 * @param uploadsPlaylistId The ID of the playlist containing the channel's uploads.
 */
public record Author(
        String handle,
        String name,
        String channelId,
        long subscriberCount,
        String uploadsPlaylistId
) {}