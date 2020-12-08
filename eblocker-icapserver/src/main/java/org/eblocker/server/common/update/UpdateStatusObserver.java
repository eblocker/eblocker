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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.systemstatus.ExecutionState;
import org.eblocker.server.http.service.SystemStatusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * The UpdateStatusObserver is notified when a system update starts.
 * It sets the ExecutionState to UPDATING, waits until the update is finished and
 * resets the ExecutionState to the value it had before.
 */
@Singleton
public class UpdateStatusObserver {
    private static final Logger log = LoggerFactory.getLogger(UpdateStatusObserver.class);

    private final SystemStatusService systemStatusService;
    private final ScheduledExecutorService scheduledExecutorService;
    private final long fixedDelay;
    private ScheduledFuture checkingTask;

    @Inject
    public UpdateStatusObserver(SystemStatusService systemStatusService,
                                @Named("update.observer.fixedDelay") long fixedDelay,
                                @Named("lowPrioScheduledExecutor") ScheduledExecutorService scheduledExecutorService) {
        this.systemStatusService = systemStatusService;
        this.scheduledExecutorService = scheduledExecutorService;
        this.fixedDelay = fixedDelay;
    }

    public synchronized void updateStarted(SystemUpdater updater) {
        if (checkingTask != null) {
            return; // task already running
        }

        ExecutionState stateBeforeUpdating = systemStatusService.getExecutionState();
        systemStatusService.setExecutionState(ExecutionState.UPDATING);
        checkingTask = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (updater.getUpdateStatus() != SystemUpdater.State.UPDATING) {
                    systemStatusService.setExecutionState(stateBeforeUpdating);
                    checkingTask.cancel(true);
                    checkingTask = null;
                }
            } catch (IOException e) {
                log.info("Unknown error while checking the update status", e);
            } catch (InterruptedException e) {
                log.info("Was interrupted checking the update status", e);
                Thread.currentThread().interrupt();
            }
        }, fixedDelay, fixedDelay, TimeUnit.SECONDS);
    }
}
