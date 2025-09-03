package si.jumis.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An abstraction for {@link Thread#sleep(long)} to allow for easier testing of time-dependent code.
 */
public interface Sleeper {
    /**
     * Pauses the current thread for the specified number of milliseconds.
     *
     * @param millis the length of time to sleep in milliseconds
     * @throws InterruptedException if any thread has interrupted the current thread.
     */
    void sleep(long millis) throws InterruptedException;

    /**
     * A default implementation of Sleeper that calls {@link Thread#sleep(long)}.
     */
    class DefaultSleeper implements Sleeper {
        private static final Logger logger = LoggerFactory.getLogger(DefaultSleeper.class);
        @Override
        public void sleep(long millis) throws InterruptedException {
            if (millis > 0) {
                // Log the sleep duration in seconds.
                logger.info("Waiting for {} seconds.", millis / 1000.0);
            }
            Thread.sleep(millis);
        }
    }
}