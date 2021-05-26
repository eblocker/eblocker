package org.eblocker.server.common.recorder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Map;

@Singleton
public class DomainRequestRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(DomainRequestRecorder.class);

    // Mapping: deviceId => domain => requests
    private Map<String, Map<String, RecordedDomainRequests>> recordedDomainRequests;

    @Inject
    public DomainRequestRecorder() {
        recordedDomainRequests = new Hashtable<>();
    }

    public void recordRequest(String deviceId, String domain, boolean blocked) {
        Instant timestamp = Instant.now();

        recordedDomainRequests
                .computeIfAbsent(deviceId, k -> new Hashtable<>())
                .computeIfAbsent(domain, d -> new RecordedDomainRequests(domain))
                .update(blocked);
    }

    public List<RecordedDomainRequests> getRecordedDomainRequests(String deviceId) {
        if (recordedDomainRequests.get(deviceId) == null) {
            return List.of();
        }
        return new ArrayList<>(recordedDomainRequests.get(deviceId).values());
    }

    public void resetRecording(String deviceId) {
        recordedDomainRequests.remove(deviceId);
    }
}
