package org.eblocker.server.common.data;

import org.eblocker.server.common.recorder.RecordedDomainBin;

import java.util.Set;

public interface DomainRecordingDataSource {
    Set<RecordedDomainBin> getBins(String deviceId);
    void save(String deviceId, RecordedDomainBin bin);
    void removeBins(String deviceId);
}
