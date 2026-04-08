/*
 * Copyright 2026 eBlocker Open Source GmbH
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
package org.eblocker.server.common.squid;

import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.system.ScriptRunner;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mockito;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Delayed;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

class SquidReloadingServiceTest {
    private SquidReloadingService reloadingService;
    private ScheduledExecutorService executorService;
    private ScriptRunner scriptRunner;
    private Integer graceTimeBeforeReloads = 200;
    private Integer minimumTimeBetweenReloads = 2000;
    private final String squidReconfigureScript = "squidReconfigureScript";
    private TestClock clock;

    @BeforeEach
    void setUp() {
        clock = new TestClock(LocalDateTime.now());
        scriptRunner = Mockito.mock(ScriptRunner.class);
        executorService = Mockito.mock(ScheduledExecutorService.class);

        reloadingService = new SquidReloadingService(
                scriptRunner,
                squidReconfigureScript,
                executorService,
                clock,
                graceTimeBeforeReloads,
                minimumTimeBetweenReloads);

    }

    @Test
    public void testMinimumTimeBetweenReloads() {
        // create mock future
        MockFuture future = new MockFuture();
        Mockito.when(executorService.schedule(Mockito.any(Runnable.class), Mockito.anyLong(), Mockito.any(TimeUnit.class)))
                .thenReturn(future);
        // run first squid reload
        clock.setInstant(Instant.now());
        reloadingService.tellSquidToReloadConfig();

        // reload must have been scheduled after grace period
        ArgumentCaptor<Runnable> captor = ArgumentCaptor.forClass(Runnable.class);
        InOrder inOrder = Mockito.inOrder(executorService);
        inOrder.verify(executorService).schedule(captor.capture(), Mockito.eq((long) graceTimeBeforeReloads), Mockito.eq(TimeUnit.MILLISECONDS));
        // actually run update
        captor.getValue().run();

        // run second reload within grace period
        clock.setInstant(clock.instant().plus(100, ChronoUnit.MILLIS));
        future.setDelay(100L);  // previous reload (1) is still in grace period
        reloadingService.tellSquidToReloadConfig();
        inOrder.verifyNoMoreInteractions();

        // run third reload after grace period but within minimum reload time
        clock.setInstant(clock.instant().plus(200, ChronoUnit.MILLIS));
        future.setDelay(-100L);  // reload (1) is in progress
        reloadingService.tellSquidToReloadConfig();
        inOrder.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq(2000L), Mockito.eq(TimeUnit.MILLISECONDS));

        // run fourth reload within minimum reload time of first one -> should be ignored as one is already scheduled
        clock.setInstant(clock.instant().plus(100, ChronoUnit.MILLIS));
        future.setDelay(1900L); // previous scheduled reload (3) has not been started yet
        reloadingService.tellSquidToReloadConfig();
        inOrder.verifyNoMoreInteractions();

        // run fifth reload after minimum reload time
        clock.setInstant(clock.instant().plus(2 * minimumTimeBetweenReloads, ChronoUnit.MILLIS));
        future.setDone(true);
        reloadingService.tellSquidToReloadConfig();
        inOrder.verify(executorService).schedule(Mockito.any(Runnable.class), Mockito.eq((long) graceTimeBeforeReloads), Mockito.eq(TimeUnit.MILLISECONDS));

    }

    private static class MockFuture<V> implements ScheduledFuture<V> {
        private boolean done;
        private long delay;

        @Override
        public long getDelay(TimeUnit unit) {
            return delay;
        }

        public void setDelay(long delay) {
            this.delay = delay;
        }

        @Override
        public int compareTo(Delayed o) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isCancelled() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isDone() {
            return done;
        }

        public void setDone(boolean done) {
            this.done = done;
        }

        @Override
        public V get() {
            throw new UnsupportedOperationException();
        }

        @Override
        public V get(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }
    }
}