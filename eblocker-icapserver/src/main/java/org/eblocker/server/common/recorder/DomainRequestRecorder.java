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
package org.eblocker.server.common.recorder;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import org.eblocker.server.common.data.DomainRecordingDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;
import java.util.stream.Stream;

/**
 * Records requested domains in bins of a certain duration (e.g. one hour).
 * Bins expire after a configured time (e.g. one day).
 */
@Singleton
public class DomainRequestRecorder {
    private static final Logger LOG = LoggerFactory.getLogger(DomainRequestRecorder.class);
    private final DomainRecordingDataSource dataSource;
    private final Clock clock;
    private final long binLengthInSeconds;

    // Mapping: deviceId => RecordedDomainBin
    private Map<String, RecordedDomainBin> currentBins;

    @Inject
    public DomainRequestRecorder(@Named("domainRecorder.binLengthInSeconds") long binLengthInSeconds,
                                 DomainRecordingDataSource dataSource,
                                 Clock clock) {
        this.dataSource = dataSource;
        this.clock = clock;
        currentBins = new Hashtable<>();
        this.binLengthInSeconds = binLengthInSeconds;
    }

    public synchronized void recordRequest(String deviceId, String domain, boolean blocked) {
        Instant now = clock.instant();

        RecordedDomainBin bin = currentBins.computeIfAbsent(deviceId, k -> loadOrCreateBin(k, now));
        if (!bin.isWritable(now)) {
            dataSource.save(deviceId, currentBins.remove(deviceId));
            bin = createBin(now);
            currentBins.put(deviceId, bin);
        }
        bin.update(domain, blocked);
    }

    private RecordedDomainBin loadOrCreateBin(String deviceId, Instant now) {
        return dataSource.getBins(deviceId).stream()
                .filter(bin -> bin.isWritable(now))
                .findAny()
                .orElse(createBin(now));
    }

    private RecordedDomainBin createBin(Instant begin) {
        return new RecordedDomainBin(begin, begin.plusSeconds(binLengthInSeconds));
    }

    public Map<String, RecordedDomainCounter> getRecordedDomainRequests(String deviceId) {
        Stream<RecordedDomainBin> bins = dataSource.getBins(deviceId).stream()
                .sorted(Comparator.comparing(RecordedDomainBin::getEnd));

        RecordedDomainBin current = currentBins.get(deviceId);
        if (current != null) {
            bins = Stream.concat(
                    // the current bin might have been saved already, so remove it:
                    bins.filter(bin -> !bin.getBegin().equals(current.getBegin())),
                    Stream.of(current)
            );
        }
        Map<String, RecordedDomainCounter> result = new HashMap<>();
        bins.forEach(bin -> merge(result, bin.getRequests()));
        return result;
    }

    private void merge(Map<String, RecordedDomainCounter> result, Map<String, RecordedDomainCounter> requests) {
        for (String domain: requests.keySet()) {
            RecordedDomainCounter counter = result.computeIfAbsent(domain, d -> new RecordedDomainCounter());
            counter.update(requests.get(domain));
        }
    }

    public synchronized void resetRecording(String deviceId) {
        dataSource.removeBins(deviceId);
        currentBins.remove(deviceId);
    }

    public synchronized void saveCurrent() {
        currentBins.forEach(dataSource::save);
    }
}
