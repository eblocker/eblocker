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
package org.eblocker.server.common.update;

import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.http.service.SystemStatusService;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class UpdateStatusObserverTest {
    private static final long FIXED_DELAY = 5L;
    private SystemStatusService systemStatusService;
    private ScheduledExecutorService scheduledExecutorService;
    private UpdateStatusObserver observer;
    private SystemUpdater systemUpdater;
    private ScheduledFuture observerTask;

    @Before
    public void setUp() throws Exception {
        systemStatusService = Mockito.mock(SystemStatusService.class);
        scheduledExecutorService = Mockito.mock(ScheduledExecutorService.class);
        observerTask = Mockito.mock(ScheduledFuture.class);
        observer = new UpdateStatusObserver(systemStatusService, FIXED_DELAY, scheduledExecutorService);
        systemUpdater = Mockito.mock(SystemUpdater.class);
    }

    @Test
    public void updateStarted() throws Exception {
        Mockito.when(systemStatusService.getExecutionState()).thenReturn(ExecutionState.RUNNING);
        Mockito.when(scheduledExecutorService.scheduleWithFixedDelay(
                Mockito.any(),
                Mockito.eq(FIXED_DELAY),
                Mockito.eq(FIXED_DELAY),
                Mockito.eq(TimeUnit.SECONDS))).thenReturn(observerTask);

        observer.updateStarted(systemUpdater);

        ArgumentCaptor<Runnable> runnable = ArgumentCaptor.forClass(Runnable.class);
        Mockito.verify(scheduledExecutorService).scheduleWithFixedDelay(
                runnable.capture(),
                Mockito.eq(FIXED_DELAY),
                Mockito.eq(FIXED_DELAY),
                Mockito.eq(TimeUnit.SECONDS)
        );

        // The SystemStatusService is notified
        Mockito.verify(systemStatusService).setExecutionState(ExecutionState.UPDATING);

        // The system updater is still updating...
        Mockito.reset(systemStatusService);
        Mockito.when(systemUpdater.getUpdateStatus()).thenReturn(SystemUpdater.State.UPDATING);
        runnable.getValue().run();
        Mockito.verifyZeroInteractions(systemStatusService);

        // ... now it has finished updating
        Mockito.reset(systemStatusService);
        Mockito.when(systemUpdater.getUpdateStatus()).thenReturn(SystemUpdater.State.IDLING);
        runnable.getValue().run();
        Mockito.verify(systemStatusService).setExecutionState(ExecutionState.RUNNING);
        Mockito.verify(observerTask).cancel(true);
    }
}
