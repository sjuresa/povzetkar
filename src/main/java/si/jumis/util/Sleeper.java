package si.jumis.util;

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
        @Override
        public void sleep(long millis) throws InterruptedException {
            Thread.sleep(millis);
        }
    }
}