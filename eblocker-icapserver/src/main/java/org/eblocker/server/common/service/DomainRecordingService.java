/*
 * Copyright 2021 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.executor.NamedRunnable;
import org.eblocker.server.common.recorder.DomainRequestRecorder;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Logs requests to domains for devices that have the "domainRecordingEnabled" flag.
 * Log entries are added to a queue and processed in the background.
 */
@Singleton
public class DomainRecordingService {
    private final DomainRequestRecorder recorder;
    private final BlockingQueue<DomainRequestLogEntry> queue;

    @Inject
    public DomainRecordingService(DomainRequestRecorder recorder,
                                  @Named("unlimitedCachePoolExecutor") Executor executor) {
        this.recorder = recorder;
        queue = new LinkedBlockingDeque<>();
        runRecorderInBackground(executor);
    }

    private void runRecorderInBackground(Executor executor) {
        executor.execute(new NamedRunnable("DomainRecordingService", () -> {
            try {
                while (true) {
                    DomainRequestLogEntry entry = queue.take();
                    recorder.recordRequest(entry.deviceId, entry.hostname, entry.blocked);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }));
    }

    public void log(Device device, String hostname, boolean blocked) {
        if (device.isDomainRecordingEnabled()) {
            queue.add(new DomainRequestLogEntry(device.getId(), hostname, blocked));
        }
    }

    private static class DomainRequestLogEntry {
        private final String deviceId;
        private final String hostname;
        private final boolean blocked;

        private DomainRequestLogEntry(String deviceId, String hostname, boolean blocked) {
            this.deviceId = deviceId;
            this.hostname = hostname;
            this.blocked = blocked;
        }
    }
}
