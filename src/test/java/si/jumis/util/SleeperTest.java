package si.jumis.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class SleeperTest {

    @Test
    @DisplayName("DefaultSleeper should pause the thread for the specified duration")
    @Timeout(value = 100, unit = TimeUnit.MILLISECONDS)
    void defaultSleeper_sleepsForDuration() {
        Sleeper sleeper = new Sleeper.DefaultSleeper();
        // Asserts that the sleep method completes without throwing an exception.
        // The @Timeout ensures the test doesn't hang indefinitely.
        assertDoesNotThrow(() -> sleeper.sleep(10));
    }

    @Test
    @DisplayName("DefaultSleeper should throw InterruptedException when thread is interrupted")
    void defaultSleeper_throwsInterruptedException_whenInterrupted() throws InterruptedException {
        Sleeper sleeper = new Sleeper.DefaultSleeper();
        final Thread testThread = Thread.currentThread();

        // Create a thread that will interrupt this test thread.
        Thread interrupter = new Thread(testThread::interrupt);

        // Start the interrupter. It will run concurrently.
        interrupter.start();

        // Assert that calling sleep() on a thread that is about to be interrupted
        // throws the correct exception. We sleep for a long time to ensure the
        // interrupter thread has a chance to execute.
        assertThrows(InterruptedException.class, () -> sleeper.sleep(TimeUnit.SECONDS.toMillis(5)));

        // Clean up by joining the interrupter thread to ensure it has completed.
        interrupter.join();

        // Verify that the interrupted status has been cleared by the thrown InterruptedException.
        assertFalse(Thread.currentThread().isInterrupted(), "Interrupted status should be cleared after InterruptedException is caught.");
    }
}