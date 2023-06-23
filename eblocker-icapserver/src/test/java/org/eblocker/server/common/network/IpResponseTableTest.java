/*
 * Copyright 2023 eBlocker Open Source UG (haftungsbeschraenkt)
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
package org.eblocker.server.common.network;

import org.eblocker.server.common.data.IpAddress;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IpResponseTableTest {
    private static final String hwAddr1 = "abcdef111111";
    private static final String hwAddr2 = "abcdef222222";
    private static final IpAddress ipAddr1 = IpAddress.parse("192.168.1.2");
    private static final IpAddress ipAddr2 = IpAddress.parse("2000::2");
    private static final IpAddress ipAddr3 = IpAddress.parse("2000::3");

    private IpResponseTable table;

    @Before
    public void setUp() {
        table = new IpResponseTable();
    }

    @Test
    public void testPut() {
        table.put(hwAddr1, ipAddr1, 1234);
        table.put(hwAddr1, ipAddr2, 1236);
        Assert.assertEquals(Long.valueOf(1234), table.get(hwAddr1, ipAddr1));
        Assert.assertEquals(Long.valueOf(1236), table.get(hwAddr1, ipAddr2));
        Assert.assertNull(table.get(hwAddr1, ipAddr3));
        Assert.assertNull(table.get(hwAddr2, ipAddr1));
    }

    @Test
    public void testActiveSince() {
        table.put(hwAddr1, ipAddr1, 1234);
        table.put(hwAddr1, ipAddr2, 1236);
        Assert.assertTrue(table.activeSince(hwAddr1, 1236));
        Assert.assertFalse(table.activeSince(hwAddr1, 1237));
    }

    @Test
    public void testLatestTimestamp() {
        table.put(hwAddr1, ipAddr1, 1234);
        table.put(hwAddr1, ipAddr2, 1236);
        Assert.assertEquals(Long.valueOf(1236), table.latestTimestamp(hwAddr1));
        Assert.assertNull(table.latestTimestamp(hwAddr2));

        // lastest timestamp survives even if all IP addresses are removed:
        table.removeAll(hwAddr1, List.of(ipAddr1, ipAddr2));
        Assert.assertEquals(Long.valueOf(1236), table.latestTimestamp(hwAddr1));

        // latest timestamp is only removed if the hardware address is removed explicitly:
        table.remove(hwAddr1);
        Assert.assertNull(table.latestTimestamp(hwAddr1));
    }

    @Test
    public void testForEach() {
        table.put(hwAddr1, ipAddr1, 1000);
        table.put(hwAddr2, ipAddr1, 1233);
        table.put(hwAddr2, ipAddr2, 1235);
        table.put(hwAddr2, ipAddr3, 1237);
        Set<String> result = new HashSet<>();
        table.forEachActiveSince(1234, (hardwareAddress, ipAddresses) -> {
            ipAddresses.forEach(ipAddress -> result.add(hardwareAddress + "->" + ipAddress));
        });
        Set<String> expected = Set.of(
                hwAddr2 + "->" + ipAddr2,
                hwAddr2 + "->" + ipAddr3);
        Assert.assertEquals(expected, result);
    }

    @Test
    public void testNotifyListeners() {
        List<String> listenerCalls = new ArrayList<>();
        table.addLatestTimestampUpdateListener(((hardwareAddress, millis) -> {
            listenerCalls.add(hardwareAddress + " @ " + millis);
        }));
        table.put(hwAddr1, ipAddr1, 1234);
        table.put(hwAddr1, ipAddr1, 2234);
        table.put(hwAddr1, ipAddr2, 50000);
        table.put(hwAddr1, ipAddr2, 62000);
        table.put(hwAddr1, ipAddr1, 70000);
        List<String> expected = List.of(hwAddr1 + " @ 1234", hwAddr1 + " @ 62000");
        Assert.assertEquals(expected, listenerCalls);
    }
}
