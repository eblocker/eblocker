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
