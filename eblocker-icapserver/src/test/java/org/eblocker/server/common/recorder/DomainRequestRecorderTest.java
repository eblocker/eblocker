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

    @Override
    protected void doSetup() {
        super.doSetup();
        objectMapper.registerModule(new JavaTimeModule());
        dataSource = new JedisDomainRecordingDataSource(jedisPool, objectMapper, 30);
    }

    @Before
    public void setUp() {
        clock = new TestClock(LocalDateTime.now());
        recorder = new DomainRequestRecorder(10, dataSource, clock);
    }

    @Test
    public void recordDomains() {
        for (int i = 0; i < 10; i++) {
            recorder.recordRequest(deviceId1, "tracker.com", true);
        }
        recorder.recordRequest(deviceId2, "eblocker.org", false);

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
        recorder.recordRequest(deviceId1, "tracker.com", false);
        clock.setInstant(clock.instant().plusSeconds(12)); // force new bin
        recorder.recordRequest(deviceId1, "tracker.com", true);

        RecordedDomainCounter c1 = recorder.getRecordedDomainRequests(deviceId1).get("tracker.com");
        Assert.assertTrue(c1.isBlocked());
        Assert.assertEquals(2, c1.getCount());
    }

    @Test
    public void dontCountSavedCurrentBinTwice() throws IOException {
        recorder.recordRequest(deviceId1, "tracker.com", true);
        recorder.saveCurrent();

        RecordedDomainCounter c1 = recorder.getRecordedDomainRequests(deviceId1).get("tracker.com");
        Assert.assertTrue(c1.isBlocked());
        Assert.assertEquals(1, c1.getCount());
    }

    @Test
    public void reset() {
        recorder.recordRequest(deviceId1, "tracker.com", false);
        clock.setInstant(clock.instant().plusSeconds(12)); // force new bin
        recorder.recordRequest(deviceId1, "tracker.com", true);

        recorder.resetRecording(deviceId1);

        Assert.assertTrue(recorder.getRecordedDomainRequests(deviceId1).isEmpty());
    }
}