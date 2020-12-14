/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class MockScheduledExecutorServiceTest {
    private MockScheduledExecutorService service;

    @Before
    public void setUp() throws Exception {
        // Every task takes 2 seconds to run:
        service = new MockScheduledExecutorService(Duration.ofSeconds(2L));
    }

    @After
    public void tearDown() throws Exception {
    }

    class SimpleCommand implements Runnable {
        boolean hasRun = false;

        @Override
        public void run() {
            hasRun = true;
        }
    }

    @Test
    public void scheduleRunnable() {
        SimpleCommand command = new SimpleCommand();
        ScheduledFuture<?> future = service.schedule(command, 3, TimeUnit.SECONDS);

        // not done yet
        assertFalse(command.hasRun);
        assertFalse(future.isDone());

        service.elapse(Duration.ofSeconds(3));

        // still not done, because the task takes some time
        assertFalse(command.hasRun);
        assertFalse(future.isDone());

        service.elapse(Duration.ofSeconds(1));

        // still not done, because the task is still running
        assertFalse(command.hasRun);
        assertFalse(future.isDone());

        service.elapse(Duration.ofSeconds(1));

        // finally!
        assertTrue(command.hasRun);
        assertTrue(future.isDone());
    }

    @Test
    public void testDelays() {
        SimpleCommand command = new SimpleCommand();

        ScheduledFuture<?> future = service.schedule(command, 3, TimeUnit.SECONDS);
        assertEquals(3L, future.getDelay(TimeUnit.SECONDS));
        assertEquals(3000L, future.getDelay(TimeUnit.MILLISECONDS));

        service.elapse(Duration.ofSeconds(1));
        assertEquals(2L, future.getDelay(TimeUnit.SECONDS));

        service.elapse(Duration.ofSeconds(1));
        assertEquals(1L, future.getDelay(TimeUnit.SECONDS));

        service.elapse(Duration.ofSeconds(1));
        assertEquals(0L, future.getDelay(TimeUnit.SECONDS));

        // now the command is started (but it still takes 2 seconds to complete)
        assertFalse(command.hasRun);
        assertFalse(future.isDone());

        service.elapse(Duration.ofSeconds(2));
        assertTrue(command.hasRun);
        assertTrue(future.isDone());
    }

    @Test
    public void cancelRunnable() {
        SimpleCommand cmd1 = new SimpleCommand();
        SimpleCommand cmd2 = new SimpleCommand();

        ScheduledFuture<?> future1 = service.schedule(cmd1, 3, TimeUnit.SECONDS);
        ScheduledFuture<?> future2 = service.schedule(cmd2, 3, TimeUnit.SECONDS);

        service.elapse(Duration.ofSeconds(4));
        // now the tasks should be running...

        assertTrue(future1.cancel(true));
        assertFalse(future2.cancel(false)); // must not be interrupted

        assertTrue(future1.isCancelled());
        assertTrue(future1.isDone());
        assertFalse(cmd1.hasRun);

        // cmd2 is not done yet:
        assertFalse(future2.isCancelled());
        assertFalse(future2.isDone());
        assertFalse(cmd2.hasRun);

        // now the second command should be finished
        service.elapse(Duration.ofSeconds(1));
        assertFalse(future2.isCancelled());
        assertTrue(future2.isDone());
        assertTrue(cmd2.hasRun);

    }
}
