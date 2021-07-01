package org.eblocker.server.common.scheduler;

import com.google.inject.Inject;
import com.google.inject.name.Named;
import org.eblocker.server.common.recorder.DomainRequestRecorder;

/**
 * Ensures that recorded domains are saved to Redis regularly (and on shutdown).
 */
public class RecordedDomainsWriteScheduler extends FixedRateScheduler {
    private final DomainRequestRecorder recorder;

    @Inject
    public RecordedDomainsWriteScheduler(@Named("executor.recorded.domains.writer.startupDelay") long initialDelayInSeconds,
                                         @Named("executor.recorded.domains.writer.fixedDelay") long periodInSeconds,
                                         DomainRequestRecorder recorder) {
        super(recorder::saveCurrent, initialDelayInSeconds, periodInSeconds);
        this.recorder = recorder;
    }

    public void shutdown() {
        recorder.saveCurrent();
    }
}
