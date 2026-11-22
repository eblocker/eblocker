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
import org.eblocker.server.common.system.FatalErrorHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingDeque;

/**
 * Logs requests to domains for devices that have the "domainRecordingEnabled" flag.
 * Log entries are added to a queue and processed in the background.
 */
@Singleton
public class DomainRecordingService {
    private static final Logger log = LoggerFactory.getLogger(DomainRecordingService.class);

    private final DomainRequestRecorder recorder;
    private final BlockingQueue<DomainRequestLogEntry> queue;
    private final FatalErrorHandler fatalErrorHandler;

    public DomainRecordingService(DomainRequestRecorder recorder, Executor executor) {
        this(recorder, executor, FatalErrorHandler.noOp());
    }

    @Inject
    public DomainRecordingService(DomainRequestRecorder recorder,
                                  @Named("unlimitedCachePoolExecutor") Executor executor,
                                  FatalErrorHandler fatalErrorHandler) {
        this.recorder = recorder;
        queue = new LinkedBlockingDeque<>();
        this.fatalErrorHandler = fatalErrorHandler != null ? fatalErrorHandler : FatalErrorHandler.noOp();
        runRecorderInBackground(executor);
    }

    private void runRecorderInBackground(Executor executor) {
        executor.execute(new NamedRunnable("DomainRecordingService", () -> {
            while (true) {
                try {
                    DomainRequestLogEntry entry = queue.take();
                    recorder.recordRequest(entry.deviceId, entry.hostname, entry.blocked, entry.patternRequest);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                } catch (Exception e) {
                    log.error("Failed to record domain request; continuing", e);
                } catch (Error e) {
                    fatalErrorHandler.onFatalError("DomainRecordingService", e);
                    throw e;
                }
            }
        }));
    }

    public void log(Device device, String hostname, boolean blocked, boolean patternRequest) {
        if (device.isDomainRecordingEnabled()) {
            queue.add(new DomainRequestLogEntry(device.getId(), hostname, blocked, patternRequest));
        }
    }

    private static class DomainRequestLogEntry {
        private final String deviceId;
        private final String hostname;
        private final boolean blocked;
        private final boolean patternRequest;

        private DomainRequestLogEntry(String deviceId, String hostname, boolean blocked, boolean patternRequest) {
            this.deviceId = deviceId;
            this.hostname = hostname;
            this.blocked = blocked;
            this.patternRequest = patternRequest;
        }
    }
}
