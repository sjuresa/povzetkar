package com.povzetkar.quarkus.service.transcript;

import java.time.Duration;
import java.util.Objects;
import java.util.random.RandomGenerator;

/** A closed range of pause lengths a random pause is drawn from (POV-222); millisecond resolution. */
public record PauseRange(Duration min, Duration max) {

    public PauseRange {
        Objects.requireNonNull(min, "min");
        Objects.requireNonNull(max, "max");
        if (min.isNegative() || max.isNegative()) {
            throw new IllegalArgumentException("Pause range must not be negative: " + min + ".." + max);
        }
        if (min.compareTo(max) > 0) {
            throw new IllegalArgumentException("Pause range min must not exceed max: " + min + ".." + max);
        }
    }

    /** A uniformly random pause within [min, max]. */
    public Duration draw(RandomGenerator random) {
        return Duration.ofMillis(random.nextLong(min.toMillis(), max.toMillis() + 1));
    }
}
