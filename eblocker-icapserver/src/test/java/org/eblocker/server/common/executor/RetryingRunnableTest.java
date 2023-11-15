package org.eblocker.server.common.executor;

import org.junit.Before;
import org.junit.Test;

public class RetryingRunnableTest {
    private FailingRunnable runnable;

    @Before
    public void setUp() {
        runnable = new FailingRunnable();
    }

    @Test(expected = RuntimeException.class)
    public void zeroRetries() {
        runnable.fail(1);
        new RetryingRunnable(runnable, 0).run();
    }

    @Test
    public void withRetries() {
        Runnable retrying = new RetryingRunnable(runnable, 3);
        retrying.run();
        runnable.fail(2);
        for (int i = 0; i < 3; i++) {
            retrying.run();
        }
        // should have recovered, now fail again
        runnable.fail(3);
        for (int i = 0; i < 4; i++) {
            retrying.run();
        }
        // still OK
    }

    @Test(expected = RuntimeException.class)
    public void tooManyFailures() {
        Runnable retrying = new RetryingRunnable(runnable, 3);
        retrying.run();
        runnable.fail(4);
        for (int i = 0; i < 4; i++) {
            retrying.run();
        }
    }

    private class FailingRunnable implements Runnable {
        int timesToFail = 0;

        @Override
        public void run() {
            if (timesToFail > 0) {
                timesToFail--;
                throw new RuntimeException("Oops");
            }
        }

        public void fail(int times) {
            timesToFail = times;
        }
    }
}