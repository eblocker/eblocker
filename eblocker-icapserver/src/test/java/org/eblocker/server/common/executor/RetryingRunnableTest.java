/*
 * Copyright 2024 eBlocker Open Source UG (haftungsbeschraenkt)
 *
 * Licensed under the EUPL, Version 1.2 or - as soon they will be
 * approved by the European Commission - subsequent versions of the EUPL
 * (the "License"); You may not use this work except in compliance with
 * the License. You may obtain a copy of the License at:
 *
 *   https://joinup.ec.europa.eu/page/eupl-text-11-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 * implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
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