/*
 * Copyright 2020 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.http.service;

import org.eblocker.server.common.data.Device;
import org.eblocker.server.common.data.TestDeviceFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import java.time.Clock;
import java.time.Instant;
import java.util.stream.Stream;

public class DeviceOnlineStatusCacheTest {
    private static final int OFFLINE_AFTER_SECONDS = 30;
    private DeviceOnlineStatusCache cache;
    private Clock clock;

    @Before
    public void setUp() throws Exception {
        clock = Mockito.mock(Clock.class);
        cache = new DeviceOnlineStatusCache(clock, OFFLINE_AFTER_SECONDS);
    }

    @Test
    public void test() {
        Device a = TestDeviceFactory.createDevice("112233445566", "192.168.1.23", true);
        Device b = TestDeviceFactory.createDevice("223344556677", "192.168.1.42", true);
        Device c = TestDeviceFactory.createDevice("334455667788", "192.168.1.70", true);
        Device d = TestDeviceFactory.createDevice("334455667788", "192.168.1.70", true);
        d.setIsVpnClient(true);
        int now = 12345678;

        // Device "a" was online but has now expired:
        setClock(now - OFFLINE_AFTER_SECONDS - 2);
        cache.updateOnlineStatus(a.getId());

        // Device "b" is still online:
        setClock(now - OFFLINE_AFTER_SECONDS + 2);
        cache.updateOnlineStatus(b.getId());

        // Device "c" is not in the cache

        setClock(now);
        Stream.of(a, b, c, d).forEach(cache::setOnlineStatus);
        Assert.assertFalse(a.isOnline());
        Assert.assertTrue(b.isOnline());
        Assert.assertFalse(c.isOnline());
        Assert.assertTrue(d.isOnline());
    }

    private void setClock(int seconds) {
        Mockito.when(clock.instant()).thenReturn(Instant.ofEpochSecond(seconds));
    }
}
