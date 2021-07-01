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