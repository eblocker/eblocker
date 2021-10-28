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

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eblocker.server.common.EmbeddedRedisTestBase;
import org.eblocker.server.common.TestClock;
import org.eblocker.server.common.data.DomainRecordingDataSource;
import org.eblocker.server.common.data.JedisDomainRecordingDataSource;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

public class DomainRequestRecorderTest extends EmbeddedRedisTestBase {
    private TestClock clock;
    private DomainRecordingDataSource dataSource;
    private DomainRequestRecorder recorder;
    private String deviceId1 = "device:abcdef012345";
    private String deviceId2 = "device:0123456789ab";
    private final int binLifetime = 30;
    private final int binLength = 10;

    @Override
    protected void doSetup() {
        super.doSetup();
        objectMapper.registerModule(new JavaTimeModule());
        dataSource = new JedisDomainRecordingDataSource(jedisPool, objectMapper, binLifetime);
    }

    @Before
    public void setUp() {
        clock = new TestClock(LocalDateTime.now());
        recorder = new DomainRequestRecorder(binLength, dataSource, clock);
    }

    @Test
    public void recordDomains() {
        for (int i = 0; i < 10; i++) {
            recorder.recordRequest(deviceId1, "tracker.com", true, false);
        }
        recorder.recordRequest(deviceId2, "eblocker.org", false, false);

        RecordedDomainCounter c1 = recorder.getRecordedDomainRequests(deviceId1).get("tracker.com");
        Assert.assertTrue(c1.isBlocked());
        Assert.assertEquals(10, c1.getCount());

        RecordedDomainCounter c2 = recorder.getRecordedDomainRequests(deviceId2).get("eblocker.org");
        Assert.assertFalse(c2.isBlocked());
        Assert.assertEquals(1, c2.getCount());
    }

    @Test
    public void emptyResult() {
        Map<String, RecordedDomainCounter> requests = recorder.getRecordedDomainRequests(deviceId1);
        Assert.assertTrue(requests.isEmpty());
    }

    @Test
    public void mergeBins() {
        recorder.recordRequest(deviceId1, "tracker.com", false, false);
        clock.setInstant(clock.instant().plusSeconds(binLength + 1)); // force new bin
        recorder.recordRequest(deviceId1, "tracker.com", true, false);

        RecordedDomainCounter c1 = recorder.getRecordedDomainRequests(deviceId1).get("tracker.com");
        Assert.assertTrue(c1.isBlocked());
        Assert.assertEquals(2, c1.getCount());
    }

    @Test
    public void dontCountSavedCurrentBinTwice() throws IOException {
        recorder.recordRequest(deviceId1, "tracker.com", true, false);
        recorder.saveCurrent();

        RecordedDomainCounter c1 = recorder.getRecordedDomainRequests(deviceId1).get("tracker.com");
        Assert.assertTrue(c1.isBlocked());
        Assert.assertEquals(1, c1.getCount());
    }

    @Test
    public void reset() {
        recorder.recordRequest(deviceId1, "tracker.com", false, false);
        clock.setInstant(clock.instant().plusSeconds(binLength + 1)); // force new bin
        recorder.recordRequest(deviceId1, "tracker.com", true, false);

        recorder.resetRecording(deviceId1);

        Assert.assertTrue(recorder.getRecordedDomainRequests(deviceId1).isEmpty());
    }

    @Test
    public void expiredBinInMemory() {
        recorder.recordRequest(deviceId1, "tracker.com", true, false);
        clock.setInstant(clock.instant().plusSeconds(binLength + binLifetime + 1)); // bin is now expired
        Assert.assertEquals(0, recorder.getRecordedDomainRequests(deviceId1).size());
    }

    @Test
    public void dontSaveExpiredBin() {
        recorder.recordRequest(deviceId1, "tracker.com", true, false);
        clock.setInstant(clock.instant().plusSeconds(binLength + binLifetime + 1)); // bin is now expired
        // continue recording
        recorder.recordRequest(deviceId1, "adserver.com", true, false);
        // the expired bin has not been saved to the database:
        Assert.assertTrue(dataSource.getBins(deviceId1).isEmpty());
    }
}
