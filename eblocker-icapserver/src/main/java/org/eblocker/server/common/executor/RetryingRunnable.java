package org.eblocker.server.common.executor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A wrapper class for Runnable that catches up to maxRetries exceptions (in a row), before it gives up
 * and re-throws the exception.
 *
 * This is useful only for Runnables that are scheduled at regular time intervals.
 */
public class RetryingRunnable implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(RetryingRunnable.class);

    private final Runnable runnable;
    private final long maxRetries;
    private int failures;

    public RetryingRunnable(Runnable runnable, long maxRetries) {
        this.runnable = runnable;
        this.maxRetries = maxRetries;
        this.failures = 0;
    }

    @Override
    public void run() {
        try {
            runnable.run();
            if (failures > 0) {
                log.info("Runnable {} recovered after {} failures.", runnable.getClass(), failures);
                failures = 0;
            }
        } catch (Exception e) {
            if (failures >= maxRetries) {
                log.error("Runnable failed permanently after {} retries. Re-throwing exception.", maxRetries, e);
                throw e;
            }
            failures++;
            log.error("Runnable failed. Will retry {} more times.", maxRetries - failures, e);
        }
    }
}
