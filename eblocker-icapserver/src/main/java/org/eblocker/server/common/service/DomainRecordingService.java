package org.eblocker.server.common.service;

import com.google.inject.Inject;
import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.pubsub.PubSubService;
import org.eblocker.server.common.recorder.DomainRequestRecorder;

import java.time.Instant;

/**
 * Logs requests to domains for devices that have the flag enabled
 */
public class DomainRecordingService {
    private final DomainRequestRecorder recorder;

    @Inject
    public DomainRecordingService(DomainRequestRecorder recorder) {
        this.recorder = recorder;
    }

    public void log(Device device, String hostname, boolean blocked) {
        if (device.isDomainRecordingEnabled()) {
            recorder.recordRequest(device.getId(), hostname, blocked);
        }
    }
}
