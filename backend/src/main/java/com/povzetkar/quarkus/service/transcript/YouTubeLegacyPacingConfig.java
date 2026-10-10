package com.povzetkar.quarkus.service.transcript;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

import java.time.Duration;

/**
 * Human-like pacing of youtube-legacy transcript fetches (POV-222): a random pause before a fetch, measured
 * from the end of the previous fetch, and a shorter random pause before each further call inside a fetch.
 * Both ranges are validated at startup by {@link YouTubeRequestPacer}.
 */
@ConfigMapping(prefix = "transcript.providers.youtube-legacy.pacing")
public interface YouTubeLegacyPacingConfig {

    @WithName("fetch.min")
    @WithDefault("15s")
    Duration fetchMin();

    @WithName("fetch.max")
    @WithDefault("45s")
    Duration fetchMax();

    @WithName("in-fetch.min")
    @WithDefault("1s")
    Duration inFetchMin();

    @WithName("in-fetch.max")
    @WithDefault("5s")
    Duration inFetchMax();

    /** Pause before a fetch; throws {@link IllegalArgumentException} for an invalid range. */
    default PauseRange fetchPause() {
        return new PauseRange(fetchMin(), fetchMax());
    }

    /** Pause before each call of a fetch except the first; throws {@link IllegalArgumentException} for an invalid range. */
    default PauseRange inFetchPause() {
        return new PauseRange(inFetchMin(), inFetchMax());
    }
}
