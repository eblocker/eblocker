package org.eblocker.server.common.data;

import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.eblocker.server.common.EmbeddedRedisTestBase;
import org.eblocker.server.common.recorder.RecordedDomainBin;
import org.junit.Test;

import java.io.IOException;
import java.time.Instant;
import java.util.Set;

import static org.junit.Assert.*;

public class JedisDomainRecordingDataSourceTest extends EmbeddedRedisTestBase {
    private JedisDomainRecordingDataSource dataSource;
    private final static String deviceId = "device:012345abcdef";

    @Override
    protected void doSetup() {
        super.doSetup();
        objectMapper.registerModule(new JavaTimeModule());
        dataSource = new JedisDomainRecordingDataSource(jedisPool, objectMapper, 86400);
    }

    @Test
    public void testBinLifecycle() throws IOException {
        Instant t0 = Instant.now();
        Instant t1 = t0.plusMillis(1000);
        Instant t2 = t1.plusMillis(1000);

        RecordedDomainBin bin1 = new RecordedDomainBin(t0, t1);
        bin1.update("tracker.com", true);
        bin1.update("eblocker.org", false);

        RecordedDomainBin bin2 = new RecordedDomainBin(t1, t2);
        bin2.update("eblocker.org", false);

        dataSource.save(deviceId, bin1);
        dataSource.save(deviceId, bin2);

        Set<RecordedDomainBin> result = dataSource.getBins(deviceId);
        assertEquals(2, result.size());
        assertTrue(result.contains(bin1));
        assertTrue(result.contains(bin2));

        dataSource.removeBins(deviceId);
        assertTrue(dataSource.getBins(deviceId).isEmpty());
    }
}